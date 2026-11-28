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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.sticker.ContraptionRenderFilter;
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
    private static final long CUT_GROUP_PREFIX   = 0x48584300_00000000L; // "HXC\0"

    private static final AtomicLong REVISION_SEQ = new AtomicLong(1L);
    private static final AtomicLong IMAGE_REV_SEQ = new AtomicLong(1L);
    private static final AtomicLong NEXT_SHELL_GROUP_ID = new AtomicLong(SHELL_GROUP_PREFIX + 1L);
    private static final AtomicLong NEXT_DECAL_GROUP_ID = new AtomicLong(DECAL_GROUP_PREFIX + 1L);
    private static final AtomicLong NEXT_CUT_GROUP_ID = new AtomicLong(CUT_GROUP_PREFIX + 1L);
    private static final Map<String, CompiledMesh> SHELL_MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<BlockState, CompiledMesh> SHELL_STATE_CACHE = new ConcurrentHashMap<>();
    private static final Map<BlockState, String> SHELL_STATE_KEY_CACHE = new ConcurrentHashMap<>();
    private static final Set<Block> SHELL_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final Map<Block, Boolean> HEXIE_BLOCK_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Map<BlockPos, Long>> SHELL_FAMILY_GROUPS = new ConcurrentHashMap<>();
    private static final Map<Long, Pose> ACTIVE_SHELL_POSES = new ConcurrentHashMap<>();
    private static final Map<Long, StructureRevision> ACTIVE_SHELL_REVISIONS = new ConcurrentHashMap<>();

    private static final Map<Long, Long> CHUNK_REV_HASH = new ConcurrentHashMap<>();
    private static final Map<Long, Long> ACTIVE_DECAL_GROUPS = new ConcurrentHashMap<>();
    private static final Map<Long, String> ACTIVE_DECAL_GEOMS = new ConcurrentHashMap<>();
    private static final Map<String, CompiledMesh> ACTIVE_DECAL_HOST_MESHES = new ConcurrentHashMap<>();
    private static final Map<Long, StructureRevision> ACTIVE_DECAL_REVISIONS = new ConcurrentHashMap<>();
    private static final Map<String, PixelImageSource> IMAGE_SOURCES = new ConcurrentHashMap<>();
    private static final Map<String, Long> IMAGE_REQUEST_TIMES = new ConcurrentHashMap<>();

    private static final Map<Long, Long> ACTIVE_CUT_GROUPS = new ConcurrentHashMap<>();
    private static final Map<Long, String> ACTIVE_CUT_GEOMS = new ConcurrentHashMap<>();
    private static final Map<String, CompiledMesh> ACTIVE_CUT_HOST_MESHES = new ConcurrentHashMap<>();
    private static final Map<Long, StructureRevision> ACTIVE_CUT_REVISIONS = new ConcurrentHashMap<>();
    private static final Map<Long, Long> ACTIVE_CUT_SIGS = new ConcurrentHashMap<>();
    private static volatile boolean cutGroupsDirty = true;
    private static long lastCutSyncTick = Long.MIN_VALUE;

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
                BlockPos pos = new BlockPos(x, y, z);
                if (BlockModelCutter.INSTANCE.hasWindowCutAt(pos)) return true;
                if (usesCachedModelShadow(state)) return true;
                return false;
            }

            @Override
            public void appendExtraTriangles(Object levelObj, int x, int y, int z, Object stateObj,
                                             List<ForwardLight.CasterTri> out) {
                if (!(levelObj instanceof Level level) || !(stateObj instanceof BlockState state)) return;
                Block block = state.getBlock();
                if (SHELL_BLOCKS.contains(block)) return;
                BlockPos pos = new BlockPos(x, y, z);
                if (BlockModelCutter.INSTANCE.hasWindowCutAt(pos)) {
                    // Externally rendered cut blocks already cast exact cut+frame shadows straight from scene.sections()!
                    if (!BlockModelCutter.INSTANCE.isExternallyRenderedCut(pos)) {
                        appendCutWindowCasterTriangles(level, pos, out);
                    }
                    return;
                }
                if (usesCachedModelShadow(state)) {
                    appendOpaqueBlockShadowTriangles(state, x, y, z, out);
                }
            }
        });
        ClientRenderState.setCeilingEmitterChecker(stateObj ->
                (stateObj instanceof BlockState state && isHeXieBlock(state.getBlock()))
                        ? BakedModelMeshCompiler.ceilingEmitterOffsetY(state) : Double.NaN);
        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            ContraptionRenderFilter.register(CreateContraptionGroups::shouldHideFromContraptionMesh);
            ClientRenderState.registerDynamicCasterProvider(HOST_ID, CreateContraptionCasters::collect);
            ClientRenderState.registerDynamicMeshProvider(HOST_ID, CreateContraptionCasters::collectDynamicMeshes);
        }
        BlockModelCutter.INSTANCE.addCutChangedListener(dirty -> {
            cutGroupsDirty = true;
            ClientRenderState.sectionRevision++;
            var mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                syncCutWindowGroups(mc.level);
            }
        });
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

    static CompiledMesh compiledShellModelByKey(String modelKey) {
        return modelKey == null ? null : SHELL_MODEL_CACHE.get(modelKey);
    }

    static String shellModelKey(BlockState state) {
        return state == null ? null : SHELL_STATE_KEY_CACHE.get(state);
    }

    static long nextRevision() {
        return REVISION_SEQ.incrementAndGet();
    }

    static boolean isHeXieBlock(Block block) {
        return HEXIE_BLOCK_CACHE.computeIfAbsent(block, b -> {
            var key = BuiltInRegistries.BLOCK.getKey(b);
            return key != null && HexieConstants.MOD_ID.equals(key.getNamespace());
        });
    }

    /**
     * Unified cached-model shadow routing: every non-full-cube HeXie block (except StaticShell OBJ parts, which
     * already cast directly from their compiled shell mesh, and invisible proxy blocks) casts shadows from its
     * cached alpha-pruned BakedModel instead of coarse 1x1 VoxelShapes or name whitelists.
     */
    static boolean usesCachedModelShadow(BlockState state) {
        if (state == null || state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) return false;
        Block block = state.getBlock();
        if (SHELL_BLOCKS.contains(block) || !isHeXieBlock(block)) return false;
        if (state.canOcclude() && Block.isShapeFullBlock(state.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
            return false;
        }
        return true;
    }

    private static void appendOpaqueBlockShadowTriangles(BlockState state, int x, int y, int z,
                                                         List<ForwardLight.CasterTri> out) {
        var localTris = BakedModelMeshCompiler.compileOpaqueShadowTris(state);
        if (localTris.isEmpty()) return;
        long idBase = BlockPos.asLong(x, y, z) << 10;
        long seq = 0;
        for (var t : localTris) {
            Vec a = new Vec(x + t.a().pos().x(), y + t.a().pos().y(), z + t.a().pos().z());
            Vec b = new Vec(x + t.b().pos().x(), y + t.b().pos().y(), z + t.b().pos().z());
            Vec c = new Vec(x + t.c().pos().x(), y + t.c().pos().y(), z + t.c().pos().z());
            out.add(new ForwardLight.CasterTri(new Triangle(idBase + (seq++), a, b, c), 0));
        }
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
            if (state != null) {
                SHELL_STATE_CACHE.remove(state);
                SHELL_STATE_KEY_CACHE.remove(state);
            }
            return;
        }
        SHELL_MODEL_CACHE.put(modelKey, compiled);
        if (state != null) {
            SHELL_STATE_CACHE.put(state, compiled);
            SHELL_STATE_KEY_CACHE.put(state, modelKey);
        }
        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            CreateContraptionGroups.clearShellBoundsCache();
        }
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
            for (long groupId : ACTIVE_CUT_GROUPS.values()) engine.removeGroup(groupId);
            for (String geomId : ACTIVE_CUT_GEOMS.values()) engine.unregisterModel(geomId);
            for (long groupId : ACTIVE_SHELL_POSES.keySet()) engine.removeGroup(groupId);
        }
        SHELL_FAMILY_GROUPS.clear();
        ACTIVE_SHELL_POSES.clear();
        ACTIVE_SHELL_REVISIONS.clear();
        CHUNK_REV_HASH.clear();
        ACTIVE_DECAL_GROUPS.clear();
        ACTIVE_DECAL_GEOMS.clear();
        ACTIVE_DECAL_HOST_MESHES.clear();
        ACTIVE_DECAL_REVISIONS.clear();
        ACTIVE_CUT_GROUPS.clear();
        ACTIVE_CUT_GEOMS.clear();
        ACTIVE_CUT_HOST_MESHES.clear();
        ACTIVE_CUT_REVISIONS.clear();
        ACTIVE_CUT_SIGS.clear();
        cutGroupsDirty = true;
        lastCutSyncTick = Long.MIN_VALUE;
        IMAGE_SOURCES.clear();
        IMAGE_REQUEST_TIMES.clear();
        BakedModelMeshCompiler.clearShadowCache();
        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            CreateContraptionGroups.onLevelUnload(engine);
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
                if (oldGeom != null) {
                    ACTIVE_DECAL_HOST_MESHES.remove(oldGeom);
                    engine.unregisterModel(oldGeom);
                }
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

        boolean allNeighborChunksLoaded = true;
        for (BlockPos bp : sampleBlocks) {
            if (!level.hasChunk(bp.getX() >> 4, bp.getZ() >> 4)) {
                allNeighborChunksLoaded = false;
                break;
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

        ACTIVE_DECAL_HOST_MESHES.put(geomId, hostMesh);
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
        if (allImagesReady && allNeighborChunksLoaded) {
            CHUNK_REV_HASH.put(chunkKey, revHash);
        }
    }

    static boolean ensureDecalImageRegistered(HexieRenderEngine engine, String hash) {
        PixelImageSource existing = IMAGE_SOURCES.get(hash);
        if (existing != null) {
            if (engine.imageSource(hash).isEmpty()) {
                engine.registerImageSource(hash, existing);
            }
            return true;
        }
        var bytesOpt = HexieBridges.sticker().clientImage(hash);
        if (bytesOpt.isEmpty()) {
            long now = System.currentTimeMillis();
            Long last = IMAGE_REQUEST_TIMES.get(hash);
            if (last == null || now - last >= 2000L) {
                IMAGE_REQUEST_TIMES.put(hash, now);
                HexieBridges.sync().sendStickerImageRequest(hash);
            }
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
            src.publish(IMAGE_REV_SEQ.incrementAndGet(), w, h, w * 4, ImageSource.RowOrder.TOP_DOWN, rgba);
            engine.registerImageSource(hash, src);
            IMAGE_SOURCES.put(hash, src);
            IMAGE_REQUEST_TIMES.remove(hash);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Groups all active non-StaticShell window-cut blocks into connected cross-block / cross-chunk clusters,
     * removes them cleanly from Sodium/vanilla chunk meshes via {@code externallyRenderedCuts}, and publishes
     * one seamless cached HeXie renderer mesh per cluster (serving BOTH rendering and raytraced shadows).
     */
    private static void syncCutWindowGroups(Level level) {
        HexieRenderEngine engine = safeEngine();
        if (engine == null || level == null) return;
        cutGroupsDirty = false;

        List<StickerEntry> windows = BlockModelCutter.INSTANCE.snapshotActiveWindowEntries();
        if (windows.isEmpty()) {
            clearActiveCutGroups(engine);
            BlockModelCutter.INSTANCE.setExternallyRenderedCuts(Set.of());
            return;
        }

        // Map every affected non-StaticShell block (plus any non-StaticShell multi-block root below an invisible member)
        // to the window(s) that cut it.
        var blockToWindows = new LinkedHashMap<BlockPos, List<StickerEntry>>();
        for (StickerEntry e : windows) {
            if (e.mode() != StickerMode.WINDOW) continue;
            for (BlockPos raw : e.affectedPositions()) {
                if (!level.hasChunk(raw.getX() >> 4, raw.getZ() >> 4)) continue;
                BlockPos target = resolveCutBlockTarget(level, raw);
                if (target == null) continue;
                blockToWindows.computeIfAbsent(target, k -> new ArrayList<>(2)).add(e);
            }
        }

        if (blockToWindows.isEmpty()) {
            clearActiveCutGroups(engine);
            BlockModelCutter.INSTANCE.setExternallyRenderedCuts(Set.of());
            return;
        }

        // Partition blocks into connected components (6-neighbor adjacency OR sharing a StickerEntry) so
        // cross-block AND cross-chunk windows are always compiled & cut as ONE seamless unified mesh!
        var visited = new HashSet<BlockPos>(blockToWindows.size());
        var windowToBlocks = new HashMap<StickerEntry, List<BlockPos>>();
        for (var entry : blockToWindows.entrySet()) {
            for (var win : entry.getValue()) {
                windowToBlocks.computeIfAbsent(win, k -> new ArrayList<>(4)).add(entry.getKey());
            }
        }

        var nextClusterKeys = new HashSet<Long>();
        var nextExternalBlocks = new HashSet<BlockPos>(blockToWindows.size());
        String dim = level.dimension().location().toString();
        RenderBridge bridge = ClientRenderState.bridge();

        for (BlockPos seed : blockToWindows.keySet()) {
            if (!visited.add(seed)) continue;
            var clusterBlocks = new LinkedHashSet<BlockPos>();
            var clusterWindows = new LinkedHashSet<StickerEntry>();
            var queue = new ArrayDeque<BlockPos>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();
                clusterBlocks.add(cur);
                List<StickerEntry> wins = blockToWindows.get(cur);
                if (wins != null) {
                    for (StickerEntry w : wins) {
                        if (clusterWindows.add(w)) {
                            for (BlockPos other : windowToBlocks.getOrDefault(w, List.of())) {
                                if (visited.add(other)) queue.add(other);
                            }
                        }
                    }
                }
                for (Direction d : Direction.values()) {
                    BlockPos nb = cur.relative(d);
                    if (blockToWindows.containsKey(nb) && visited.add(nb)) {
                        queue.add(nb);
                    }
                }
            }

            long minKey = Long.MAX_VALUE;
            var cutAxes = EnumSet.noneOf(Direction.Axis.class);
            for (BlockPos bp : clusterBlocks) {
                long k = bp.asLong();
                if (k < minKey) minKey = k;
            }
            for (StickerEntry w : clusterWindows) {
                cutAxes.add(w.facing().getAxis());
            }

            // Compute fast content + neighbor-occlusion signature so unchanged clusters never re-bake!
            long sig = 17L;
            for (BlockPos bp : clusterBlocks) {
                BlockState st = level.getBlockState(bp);
                sig = sig * 31L + bp.asLong();
                sig = sig * 31L + System.identityHashCode(st);
                for (Direction d : Direction.values()) {
                    BlockPos np = bp.relative(d);
                    if (level.hasChunk(np.getX() >> 4, np.getZ() >> 4)) {
                        BlockState nst = level.getBlockState(np);
                        sig = sig * 31L + (nst.canOcclude() ? System.identityHashCode(nst) : 0);
                    }
                }
            }
            for (StickerEntry e : clusterWindows) {
                sig = sig * 31L + e.anchorPos().asLong();
                sig = sig * 31L + e.facing().ordinal();
                sig = sig * 31L + e.revision();
                sig = sig * 31L + e.window().hashCode();
            }

            nextClusterKeys.add(minKey);
            nextExternalBlocks.addAll(clusterBlocks);

            Long existingGroup = ACTIVE_CUT_GROUPS.get(minKey);
            if (existingGroup != null && Objects.equals(ACTIVE_CUT_SIGS.get(minKey), sig) && engine.hasGroup(existingGroup)) {
                continue;
            }

            CompiledMesh hostMesh = BakedModelMeshCompiler.compileWorldBlocksForCut(level, clusterBlocks, cutAxes);
            if (hostMesh.isEmpty()) {
                nextExternalBlocks.removeAll(clusterBlocks);
                continue;
            }

            String geomId = "hexie:cut_cluster_" + Long.toUnsignedString(minKey, 16);
            ModelRef hostRef = new ModelRef(geomId, "atlas:blocks", Pose.IDENTITY);
            var openings = new ArrayList<OpeningRequest>(clusterWindows.size());
            for (StickerEntry e : clusterWindows) {
                var w = e.window();
                Vec3 fc = StickerPlaneMath.faceCenter(e.anchorPos(), e.facing());
                Vec3 inw = StickerPlaneMath.outward(e.facing()).scale(-1.0);
                Pose placement = new Pose(new Vec(fc.x, fc.y, fc.z), 0.0, 0.0, 0.0, 1.0);
                Vec inward = new Vec(inw.x, inw.y, inw.z);
                AnchorHit hit = new AnchorHit(e.revision(), 0L, 0.5, 0.5);
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
                        hostRef, placement, inward, hit, CutMode.ANCHORED_LAYER,
                        Optional.empty(), hostRef, params));
            }

            long groupId = existingGroup != null ? existingGroup : NEXT_CUT_GROUP_ID.getAndIncrement();
            ACTIVE_CUT_HOST_MESHES.put(geomId, hostMesh);
            engine.registerCompiledModel(geomId, hostMesh);
            long revNum = REVISION_SEQ.incrementAndGet();
            BlockPos first = clusterBlocks.iterator().next();
            StructureRevision revision = new StructureRevision(
                    groupId, revNum,
                    new SurfaceHostId.World(dim, new Cell(first.getX() >> 4, first.getY() >> 4, first.getZ() >> 4)),
                    List.of(hostRef), openings);
            bridge.structure(revision);

            ACTIVE_CUT_GROUPS.put(minKey, groupId);
            ACTIVE_CUT_GEOMS.put(minKey, geomId);
            ACTIVE_CUT_REVISIONS.put(groupId, revision);
            ACTIVE_CUT_SIGS.put(minKey, sig);
        }

        // Evict stale cut clusters whose windows were removed or merged
        var it = ACTIVE_CUT_GROUPS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (nextClusterKeys.contains(e.getKey())) continue;
            long gid = e.getValue();
            it.remove();
            ACTIVE_CUT_REVISIONS.remove(gid);
            ACTIVE_CUT_SIGS.remove(e.getKey());
            engine.removeGroup(gid);
            String oldGeom = ACTIVE_CUT_GEOMS.remove(e.getKey());
            if (oldGeom != null) {
                ACTIVE_CUT_HOST_MESHES.remove(oldGeom);
                engine.unregisterModel(oldGeom);
            }
        }

        BlockModelCutter.INSTANCE.setExternallyRenderedCuts(nextExternalBlocks);
    }

    private static BlockPos resolveCutBlockTarget(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;
        if (SHELL_BLOCKS.contains(state.getBlock())) return null;
        if (state.getRenderShape() != RenderShape.INVISIBLE) return pos.immutable();
        // Invisible multi-block member cell (e.g. tall 25-series panel): locate non-StaticShell root below
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos below = pos.below(dy);
            BlockState bs = level.getBlockState(below);
            if (!bs.isAir() && bs.getRenderShape() != RenderShape.INVISIBLE && !SHELL_BLOCKS.contains(bs.getBlock())) {
                return below.immutable();
            }
        }
        return null;
    }

    private static void clearActiveCutGroups(HexieRenderEngine engine) {
        if (ACTIVE_CUT_GROUPS.isEmpty()) return;
        for (long gid : ACTIVE_CUT_GROUPS.values()) engine.removeGroup(gid);
        for (String geomId : ACTIVE_CUT_GEOMS.values()) engine.unregisterModel(geomId);
        ACTIVE_CUT_GROUPS.clear();
        ACTIVE_CUT_GEOMS.clear();
        ACTIVE_CUT_HOST_MESHES.clear();
        ACTIVE_CUT_REVISIONS.clear();
        ACTIVE_CUT_SIGS.clear();
    }

    /** Called every frame by embedded hexie-renderer's ClientRenderState.presentAll(...) to supply HeXie poses & lights. */
    private static void collectHostFrame(long frameId, float partialTick,
                                         List<GroupPose> poses,
                                         List<ProceduralDeform> deforms,
                                         List<LightState> lights) {
        HexieRenderEngine engine = safeEngine();
        var mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            long tick = mc.level.getGameTime();
            if (cutGroupsDirty || (!ACTIVE_CUT_GROUPS.isEmpty() && tick != lastCutSyncTick)) {
                lastCutSyncTick = tick;
                syncCutWindowGroups(mc.level);
            }
        }
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
                if (rev != null) {
                    for (var mr : rev.models()) {
                        CompiledMesh cm = ACTIVE_DECAL_HOST_MESHES.get(mr.geometryId());
                        if (cm != null) engine.registerCompiledModel(mr.geometryId(), cm);
                    }
                    engine.structure(rev);
                }
            }
            poses.add(new GroupPose(groupId, GroupPose.ROOT, Pose.IDENTITY, Optional.empty()));
        }

        // 3. World-space poses for unified non-StaticShell window-cut clusters (removed from Sodium & cached in HeXie)
        for (long groupId : ACTIVE_CUT_GROUPS.values()) {
            if (engine != null && !engine.hasGroup(groupId)) {
                StructureRevision rev = ACTIVE_CUT_REVISIONS.get(groupId);
                if (rev != null) {
                    for (var mr : rev.models()) {
                        CompiledMesh cm = ACTIVE_CUT_HOST_MESHES.get(mr.geometryId());
                        if (cm != null) engine.registerCompiledModel(mr.geometryId(), cm);
                    }
                    engine.structure(rev);
                }
            }
            poses.add(new GroupPose(groupId, GroupPose.ROOT, Pose.IDENTITY, Optional.empty()));
        }

        // 4. Assembled Create trains & contraptions (StaticShell carriage shells, window-cut blocks, and decals)
        if (mc != null && mc.level != null && engine != null && net.neoforged.fml.ModList.get().isLoaded("create")) {
            CreateContraptionGroups.collectHostFrame(mc.level, engine, partialTick, poses);
        }
    }

    private static HexieRenderEngine safeEngine() {
        return ClientRenderState.demo != null ? ClientRenderState.engine() : null;
    }
}
