package willow.train.hexie.render.rendererbridge;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hexie.renderer.GroupPose;
import dev.hexie.renderer.ImageSource;
import dev.hexie.renderer.LightState;
import dev.hexie.renderer.PixelImageSource;
import dev.hexie.renderer.Pose;
import dev.hexie.renderer.ProceduralDeform;
import dev.hexie.renderer.StructureRevision;
import dev.hexie.renderer.StructureRevision.AnchorHit;
import dev.hexie.renderer.StructureRevision.Cell;
import dev.hexie.renderer.StructureRevision.CutMode;
import dev.hexie.renderer.StructureRevision.ModelRef;
import dev.hexie.renderer.StructureRevision.OpeningRequest;
import dev.hexie.renderer.StructureRevision.SurfaceHostId;
import dev.hexie.renderer.Triangle;
import dev.hexie.renderer.Vec;
import dev.hexie.renderer.mc.ClientRenderState;
import dev.hexie.renderer.mc.ForwardLight;
import dev.hexie.renderer.mc.HexieRenderEngine;
import dev.hexie.renderer.neoforge.HexieClient;
import dev.hexie.renderer.parse.CompiledMesh;
import dev.hexie.renderer.parse.CoplanarLayerCutter;
import dev.hexie.renderer.parse.ParametricWindowSpec;
import dev.hexie.renderer.parse.PlaneFrame;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.sticker.StickerEntry;
import willow.train.hexie.core.sticker.StickerMode;
import willow.train.hexie.core.sticker.StickerPlaneMath;
import willow.train.hexie.mc.client.renderbridge.RenderBridge;
import willow.train.hexie.render.sticker.BlockModelCutter;

/**
 * Built-in §5.6 RenderBridge adapter driving HeXie's embedded hexie-renderer engine:
 * - Renders CRH1/CRH2 carriage StaticShell sections natively via StructureRevision + CoplanarLayerCutter + StandardGeometry
 *   (replacing Flywheel StaticShellVisual & StaticShellSectionGeometry for world shells).
 * - Renders custom-sticker decals via decal_overlay StructureRevisions + PixelImageSource.
 * - Feeds nearby lit HeXie blocks (headlights, interior carriage lamps, railway signals) into PresentationFrame.lights().
 */
final class HexieRendererAdapter {
    private static final String HOST_ID = "hexie-v2";
    private static final long DECAL_GROUP_PREFIX = 0x48584400_00000000L; // "HXD\0"
    private static final long SHELL_GROUP_PREFIX = 0x48585300_00000000L; // "HXS\0"

    private static final AtomicLong REVISION_SEQ = new AtomicLong(1L);
    private static final AtomicLong NEXT_SHELL_GROUP_ID = new AtomicLong(SHELL_GROUP_PREFIX + 1L);
    private static final AtomicLong NEXT_DECAL_GROUP_ID = new AtomicLong(DECAL_GROUP_PREFIX + 1L);
    private static final Map<String, CompiledMesh> SHELL_MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<BlockState, CompiledMesh> SHELL_STATE_CACHE = new ConcurrentHashMap<>();
    private static final Set<Block> SHELL_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final Map<Block, Boolean> GLASS_OR_WINDOW_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<BlockPos, Long>> SHELL_FAMILY_GROUPS = new ConcurrentHashMap<>();
    private static final Map<Long, Pose> ACTIVE_SHELL_POSES = new ConcurrentHashMap<>();
    private static final Map<Long, StructureRevision> ACTIVE_SHELL_REVISIONS = new ConcurrentHashMap<>();

    private static final Map<Long, Long> CHUNK_REV_HASH = new ConcurrentHashMap<>();
    private static final Map<Long, Long> ACTIVE_DECAL_GROUPS = new ConcurrentHashMap<>();
    private static final Map<Long, String> ACTIVE_DECAL_GEOMS = new ConcurrentHashMap<>();
    private static final Map<Long, StructureRevision> ACTIVE_DECAL_REVISIONS = new ConcurrentHashMap<>();
    private static final Map<String, PixelImageSource> IMAGE_SOURCES = new ConcurrentHashMap<>();

    private HexieRendererAdapter() {}

    static void install(IEventBus modBus) {
        HexieClient.install(modBus);
        ClientRenderState.registerHostProvider(HOST_ID, HexieRendererAdapter::collectHostFrame);
        ClientRenderState.registerBlockCasterOverride(HOST_ID, new ClientRenderState.BlockCasterOverride() {
            @Override
            public boolean skipVoxelBox(Object levelObj, int x, int y, int z, Object stateObj) {
                if (!(stateObj instanceof BlockState state)) return false;
                Block block = state.getBlock();
                if (SHELL_BLOCKS.contains(block)) return true;
                if (BlockModelCutter.INSTANCE.hasWindowCutAt(new BlockPos(x, y, z))) return true;
                return !state.canOcclude() && isGlassOrWindowBlock(block);
            }

            @Override
            public void appendExtraTriangles(Object levelObj, int x, int y, int z, Object stateObj,
                                             List<ForwardLight.CasterTri> out) {
                if (!(levelObj instanceof Level level) || !(stateObj instanceof BlockState state)) return;
                if (SHELL_BLOCKS.contains(state.getBlock())) return;
                BlockPos pos = new BlockPos(x, y, z);
                if (!BlockModelCutter.INSTANCE.hasWindowCutAt(pos)) return;
                appendCutWindowCasterTriangles(level, pos, out);
            }
        });
        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            ClientRenderState.registerDynamicCasterProvider(HOST_ID, CreateContraptionCasters::collect);
        }
        BlockModelCutter.INSTANCE.addCutChangedListener(dirty -> ClientRenderState.sectionRevision++);
        HexieRenderEngine engine = safeEngine();
        if (engine != null) {
            SHELL_MODEL_CACHE.forEach(engine::registerCompiledModel);
        }
    }

    static boolean isShellBlock(Block block) {
        return SHELL_BLOCKS.contains(block);
    }

    static CompiledMesh compiledShellModel(BlockState state) {
        return state == null ? null : SHELL_STATE_CACHE.get(state);
    }

    static boolean isGlassOrWindowBlock(Block block) {
        return GLASS_OR_WINDOW_CACHE.computeIfAbsent(block, b -> {
            var key = BuiltInRegistries.BLOCK.getKey(b);
            if (key == null) return false;
            String path = key.getPath();
            if (path.contains("glass")) return true;
            if (path.contains("window") && !path.contains("nowindow")) return true;
            return HexieConstants.MOD_ID.equals(key.getNamespace())
                    && (path.contains("door") || path.contains("panel_member") || path.contains("part_member"));
        });
    }

    private static void appendCutWindowCasterTriangles(Level level, BlockPos pos, List<ForwardLight.CasterTri> out) {
        var entries = HexieBridges.sticker().entriesAffecting(level, pos);
        if (entries.isEmpty()) return;
        CompiledMesh mesh = BakedModelMeshCompiler.compileWorldBlocks(level, Set.of(pos), null);
        if (mesh.isEmpty()) return;
        var frames = new ArrayList<CompiledMesh>();
        for (var e : entries) {
            if (e.mode() != StickerMode.WINDOW) continue;
            var w = e.window();
            Vec3 fc = StickerPlaneMath.faceCenter(e.anchorPos(), e.facing());
            Vec3 inw = StickerPlaneMath.outward(e.facing()).scale(-1.0);
            PlaneFrame frame = PlaneFrame.fromInward(new Vec(fc.x, fc.y, fc.z), new Vec(inw.x, inw.y, inw.z));
            var spec = new ParametricWindowSpec(
                    w.frameColorRgb(), w.glassColorRgb(), w.glassAlpha(),
                    w.frameThickness(), w.cornerRadius(), w.width(), w.height(),
                    PlaneFrame.CardinalFace.fromNormal(frame.outward()),
                    w.offsetU(), w.offsetV());
            var cut = CoplanarLayerCutter.cutAndBuildWindow(mesh, frame, spec);
            mesh = cut.cutHost();
            if (!cut.windowFrame().isEmpty()) frames.add(cut.windowFrame());
        }
        long idBase = pos.asLong() << 10;
        long seq = 0;
        for (var t : mesh.triangles()) {
            if (t.translucent()) continue;
            out.add(new ForwardLight.CasterTri(new Triangle(idBase + (seq++), t.a().pos(), t.b().pos(), t.c().pos()), 0));
        }
        for (var fm : frames) {
            for (var t : fm.triangles()) {
                if (t.translucent()) continue;
                out.add(new ForwardLight.CasterTri(new Triangle(idBase + (seq++), t.a().pos(), t.b().pos(), t.c().pos()), 0));
            }
        }
    }

    static void registerShellModel(String modelKey, BakedModel model, BlockState state) {
        if (state != null) {
            SHELL_BLOCKS.add(state.getBlock());
        }
        CompiledMesh compiled = BakedModelMeshCompiler.compileBlockStateModel(model, state);
        if (compiled.isEmpty()) {
            SHELL_MODEL_CACHE.remove(modelKey);
            if (state != null) SHELL_STATE_CACHE.remove(state);
            return;
        }
        SHELL_MODEL_CACHE.put(modelKey, compiled);
        if (state != null) SHELL_STATE_CACHE.put(state, compiled);
        HexieRenderEngine engine = safeEngine();
        if (engine != null) {
            engine.registerCompiledModel(modelKey, compiled);
        }
    }

    static void publishShellSection(ClientLevel level, String familyId, BlockPos sectionPos,
                                    Map<BlockPos, String> ownersInSection, List<StickerEntry> windowsInSection) {
        HexieRenderEngine engine = safeEngine();
        if (engine == null || level == null) return;

        var familyMap = SHELL_FAMILY_GROUPS.computeIfAbsent(familyId, k -> new ConcurrentHashMap<>());
        BlockPos immSec = sectionPos.immutable();

        if (ownersInSection == null || ownersInSection.isEmpty()) {
            Long existingId = familyMap.remove(immSec);
            if (existingId != null) {
                ACTIVE_SHELL_POSES.remove(existingId);
                ACTIVE_SHELL_REVISIONS.remove(existingId);
                engine.removeGroup(existingId);
            }
            return;
        }

        long groupId = familyMap.computeIfAbsent(immSec, k -> NEXT_SHELL_GROUP_ID.getAndIncrement());
        int ox = sectionPos.getX() << 4;
        int oy = sectionPos.getY() << 4;
        int oz = sectionPos.getZ() << 4;

        var modelRefs = new ArrayList<ModelRef>(ownersInSection.size());
        for (var e : ownersInSection.entrySet()) {
            String modelKey = e.getValue();
            CompiledMesh cached = SHELL_MODEL_CACHE.get(modelKey);
            if (cached == null || cached.isEmpty()) continue;
            engine.registerCompiledModel(modelKey, cached);
            BlockPos root = e.getKey();
            Pose local = new Pose(new Vec(root.getX() - ox, root.getY() - oy, root.getZ() - oz), 0, 0, 0, 1);
            modelRefs.add(new ModelRef(modelKey, "atlas:blocks", local));
        }

        if (modelRefs.isEmpty()) {
            Long existingId = familyMap.remove(immSec);
            if (existingId != null) {
                ACTIVE_SHELL_POSES.remove(existingId);
                ACTIVE_SHELL_REVISIONS.remove(existingId);
                engine.removeGroup(existingId);
            }
            return;
        }

        var openings = new ArrayList<OpeningRequest>(windowsInSection.size());
        ModelRef targetRef = modelRefs.getFirst();
        for (var win : windowsInSection) {
            if (win.mode() != StickerMode.WINDOW) continue;
            var w = win.window();
            Vec3 fc = StickerPlaneMath.faceCenter(win.anchorPos(), win.facing());
            Vec3 inw = StickerPlaneMath.outward(win.facing()).scale(-1.0);
            Pose placement = new Pose(new Vec(fc.x - ox, fc.y - oy, fc.z - oz), 0, 0, 0, 1);
            Vec inward = new Vec(inw.x, inw.y, inw.z);
            AnchorHit hit = new AnchorHit(win.revision(), 0L, 0.5, 0.5);
            var params = new LinkedHashMap<String, Double>();
            params.put("frameColor", (double) w.frameColorRgb());
            params.put("glassColor", (double) w.glassColorRgb());
            params.put("glassAlpha", (double) w.glassAlpha());
            params.put("frameThickness", (double) w.frameThickness());
            params.put("cornerRadius", (double) w.cornerRadius());
            params.put("width", (double) w.width());
            params.put("height", (double) w.height());
            params.put("offsetU", (double) w.offsetU());
            params.put("offsetV", (double) w.offsetV());
            openings.add(new OpeningRequest(
                    targetRef, placement, inward, hit, CutMode.ANCHORED_LAYER,
                    Optional.empty(), targetRef, params));
        }

        String dim = level.dimension().location().toString();
        long revNum = REVISION_SEQ.incrementAndGet();
        StructureRevision revision = new StructureRevision(
                groupId, revNum,
                new SurfaceHostId.World(dim, new Cell(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ())),
                modelRefs, openings);
        RenderBridge bridge = ClientRenderState.bridge();
        bridge.structure(revision);

        ACTIVE_SHELL_REVISIONS.put(groupId, revision);
        ACTIVE_SHELL_POSES.put(groupId, new Pose(new Vec(ox, oy, oz), 0, 0, 0, 1));
    }

    static void clearShellFamily(String familyId) {
        HexieRenderEngine engine = safeEngine();
        var map = SHELL_FAMILY_GROUPS.remove(familyId);
        if (map != null) {
            for (long groupId : map.values()) {
                ACTIVE_SHELL_POSES.remove(groupId);
                ACTIVE_SHELL_REVISIONS.remove(groupId);
                if (engine != null) engine.removeGroup(groupId);
            }
        }
    }

    static void onLevelUnload() {
        HexieRenderEngine engine = safeEngine();
        if (engine != null) {
            for (long groupId : ACTIVE_DECAL_GROUPS.values()) engine.removeGroup(groupId);
            for (String geomId : ACTIVE_DECAL_GEOMS.values()) engine.unregisterModel(geomId);
            for (long groupId : ACTIVE_SHELL_POSES.keySet()) engine.removeGroup(groupId);
        }
        SHELL_FAMILY_GROUPS.clear();
        ACTIVE_SHELL_POSES.clear();
        ACTIVE_SHELL_REVISIONS.clear();
        CHUNK_REV_HASH.clear();
        ACTIVE_DECAL_GROUPS.clear();
        ACTIVE_DECAL_GEOMS.clear();
        ACTIVE_DECAL_REVISIONS.clear();
        IMAGE_SOURCES.clear();
        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            CreateContraptionCasters.clearCache();
        }
    }

    static void publishChunkStickers(Level level, int chunkX, int chunkZ, List<StickerEntry> entries) {
        HexieRenderEngine engine = safeEngine();
        if (engine == null || level == null) return;

        long chunkKey = (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
        if ((entries == null || entries.isEmpty()) && !ACTIVE_DECAL_GROUPS.containsKey(chunkKey)) {
            return;
        }

        List<StickerEntry> decals = null;
        if (entries != null && !entries.isEmpty()) {
            for (var e : entries) {
                if (e.mode() == StickerMode.DECAL && e.decal().hasImage()) {
                    if (decals == null) decals = new ArrayList<>();
                    decals.add(e);
                }
            }
        }

        if (decals == null || decals.isEmpty()) {
            Long oldGroup = ACTIVE_DECAL_GROUPS.remove(chunkKey);
            if (oldGroup != null) {
                engine.removeGroup(oldGroup);
                ACTIVE_DECAL_REVISIONS.remove(oldGroup);
                String oldGeom = ACTIVE_DECAL_GEOMS.remove(chunkKey);
                if (oldGeom != null) engine.unregisterModel(oldGeom);
                CHUNK_REV_HASH.remove(chunkKey);
            }
            return;
        }

        Long existingGroupId = ACTIVE_DECAL_GROUPS.get(chunkKey);
        String geomId = "hexie:decal_host_" + chunkX + "_" + chunkZ;

        long revHash = 17L;
        for (var e : decals) {
            revHash = revHash * 31L + e.id().hashCode();
            revHash = revHash * 31L + e.revision();
            revHash = revHash * 31L + e.decal().hashCode();
        }
        if (existingGroupId != null && Objects.equals(CHUNK_REV_HASH.get(chunkKey), revHash) && engine.hasGroup(existingGroupId)) {
            return;
        }

        var sampleBlocks = new LinkedHashSet<BlockPos>();
        for (var e : decals) {
            Vec3 uAxis = StickerPlaneMath.axisU(e.facing());
            BlockPos uStep = new BlockPos((int) Math.round(uAxis.x), (int) Math.round(uAxis.y), (int) Math.round(uAxis.z));
            var behind = e.facing().getOpposite();
            for (BlockPos bp : e.affectedPositions()) {
                sampleBlocks.add(bp);
                sampleBlocks.add(bp.relative(behind));
                sampleBlocks.add(bp.above());
                sampleBlocks.add(bp.below());
                sampleBlocks.add(bp.below(2));
                if (!uStep.equals(BlockPos.ZERO)) {
                    sampleBlocks.add(bp.offset(uStep));
                    sampleBlocks.add(bp.subtract(uStep));
                }
            }
        }

        CompiledMesh hostMesh = BakedModelMeshCompiler.compileWorldBlocks(level, sampleBlocks, null);
        if (hostMesh.isEmpty()) return;

        var openings = new ArrayList<OpeningRequest>(decals.size());
        ModelRef hostRef = new ModelRef(geomId, "atlas:blocks", Pose.IDENTITY);
        boolean allImagesReady = true;

        for (var e : decals) {
            var d = e.decal();
            if (!ensureDecalImageRegistered(engine, d.imageHash())) {
                allImagesReady = false;
                continue;
            }
            Vec3 fc = StickerPlaneMath.faceCenter(e.anchorPos(), e.facing());
            Vec3 inw = StickerPlaneMath.outward(e.facing()).scale(-1.0);
            Pose placement = new Pose(new Vec(fc.x, fc.y, fc.z), 0.0, 0.0, 0.0, 1.0);
            Vec inward = new Vec(inw.x, inw.y, inw.z);
            AnchorHit hit = new AnchorHit(e.revision(), 0L, 0.5, 0.5);
            Map<String, Double> params = Map.of(
                    "width", (double) d.width(),
                    "height", (double) d.height(),
                    "rotation", (double) d.rotationDegrees(),
                    "offsetU", (double) d.offsetU(),
                    "offsetV", (double) d.offsetV());
            openings.add(new OpeningRequest(
                    hostRef, placement, inward, hit, CutMode.ANCHORED_LAYER,
                    Optional.of("decal_overlay:" + d.imageHash()), hostRef, params));
        }

        if (openings.isEmpty()) return;

        long groupId = existingGroupId != null ? existingGroupId : NEXT_DECAL_GROUP_ID.getAndIncrement();

        engine.registerCompiledModel(geomId, hostMesh);
        String dim = level.dimension().location().toString();
        long revNum = REVISION_SEQ.incrementAndGet();
        StructureRevision revision = new StructureRevision(
                groupId, revNum, new SurfaceHostId.World(dim, new Cell(chunkX, 0, chunkZ)),
                List.of(hostRef), openings);
        RenderBridge bridge = ClientRenderState.bridge();
        bridge.structure(revision);

        ACTIVE_DECAL_GROUPS.put(chunkKey, groupId);
        ACTIVE_DECAL_GEOMS.put(chunkKey, geomId);
        ACTIVE_DECAL_REVISIONS.put(groupId, revision);
        if (allImagesReady) {
            CHUNK_REV_HASH.put(chunkKey, revHash);
        }
    }

    private static boolean ensureDecalImageRegistered(HexieRenderEngine engine, String hash) {
        if (IMAGE_SOURCES.containsKey(hash)) return true;
        var bytesOpt = HexieBridges.sticker().clientImage(hash);
        if (bytesOpt.isEmpty()) {
            HexieBridges.sync().sendStickerImageRequest(hash);
            return false;
        }
        try (NativeImage img = NativeImage.read(new ByteArrayInputStream(bytesOpt.get()))) {
            int w = img.getWidth(), h = img.getHeight();
            ByteBuffer rgba = ByteBuffer.allocate(w * h * 4);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    rgba.put((byte) (abgr & 0xFF));
                    rgba.put((byte) ((abgr >>> 8) & 0xFF));
                    rgba.put((byte) ((abgr >>> 16) & 0xFF));
                    rgba.put((byte) ((abgr >>> 24) & 0xFF));
                }
            }
            rgba.flip();
            PixelImageSource src = new PixelImageSource();
            src.publish(1L, w, h, w * 4, ImageSource.RowOrder.TOP_DOWN, rgba);
            engine.registerImageSource(hash, src);
            IMAGE_SOURCES.put(hash, src);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Called every frame by embedded hexie-renderer's ClientRenderState.presentAll(...) to supply HeXie poses & lights. */
    private static void collectHostFrame(long frameId, float partialTick,
                                         List<GroupPose> poses,
                                         List<ProceduralDeform> deforms,
                                         List<LightState> lights) {
        HexieRenderEngine engine = safeEngine();
        // 1. Batched CRH1/CRH2 carriage StaticShell sections (auto-restore if cleared by /hexie_render clear or reload)
        for (var e : ACTIVE_SHELL_POSES.entrySet()) {
            long groupId = e.getKey();
            if (engine != null && !engine.hasGroup(groupId)) {
                StructureRevision rev = ACTIVE_SHELL_REVISIONS.get(groupId);
                if (rev != null) {
                    for (var mr : rev.models()) {
                        CompiledMesh cm = SHELL_MODEL_CACHE.get(mr.geometryId());
                        if (cm != null) engine.registerCompiledModel(mr.geometryId(), cm);
                    }
                    engine.structure(rev);
                }
            }
            poses.add(new GroupPose(groupId, GroupPose.ROOT, e.getValue(), Optional.empty()));
        }

        // 2. World-space poses for every active custom-sticker decal overlay group
        for (long groupId : ACTIVE_DECAL_GROUPS.values()) {
            if (engine != null && !engine.hasGroup(groupId)) {
                StructureRevision rev = ACTIVE_DECAL_REVISIONS.get(groupId);
                if (rev != null) engine.structure(rev);
            }
            poses.add(new GroupPose(groupId, GroupPose.ROOT, Pose.IDENTITY, Optional.empty()));
        }
    }

    private static HexieRenderEngine safeEngine() {
        return ClientRenderState.demo != null ? ClientRenderState.engine() : null;
    }
}
