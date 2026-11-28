package willow.train.hexie.render.rendererbridge;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import dev.hexie.renderer.GroupPose;
import dev.hexie.renderer.Pose;
import dev.hexie.renderer.StructureRevision;
import dev.hexie.renderer.StructureRevision.AnchorHit;
import dev.hexie.renderer.StructureRevision.Cell;
import dev.hexie.renderer.StructureRevision.CutMode;
import dev.hexie.renderer.StructureRevision.ModelRef;
import dev.hexie.renderer.StructureRevision.OpeningRequest;
import dev.hexie.renderer.StructureRevision.SurfaceHostId;
import dev.hexie.renderer.Vec;
import dev.hexie.renderer.mc.ClientRenderState;
import dev.hexie.renderer.mc.HexieRenderEngine;
import dev.hexie.renderer.parse.CompiledMesh;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.sticker.ContraptionStickerHost;
import willow.train.hexie.core.sticker.StickerEntry;
import willow.train.hexie.core.sticker.StickerMode;
import willow.train.hexie.core.sticker.StickerPlaneMath;
import willow.train.hexie.mc.client.renderbridge.RenderBridge;

/**
 * Unified HeXie renderer group manager for assembled Create trains & contraptions:
 * - Owns StaticShell carriage shells (CRH1/CRH2) on assembled trains directly inside HexieRenderEngine
 *   (identical cached geometry, smooth normals, window cutting, and lighting to static world shells).
 * - Owns non-StaticShell window-cut clusters and projected image decals attached to assembled contraptions.
 * - Bakes local-space geometry once per contraption revision and updates only the rigid-body GroupPose per frame.
 */
final class CreateContraptionGroups {
    private static final long CONTRAPTION_SHELL_PREFIX = 0x48585400_00000000L; // "HXT\0"
    private static final long CONTRAPTION_CUT_PREFIX   = 0x48585500_00000000L; // "HXU\0"
    private static final long CONTRAPTION_DECAL_PREFIX = 0x48585600_00000000L; // "HXV\0"

    private static final AtomicLong NEXT_SHELL_ID = new AtomicLong(CONTRAPTION_SHELL_PREFIX + 1L);
    private static final AtomicLong NEXT_CUT_ID   = new AtomicLong(CONTRAPTION_CUT_PREFIX + 1L);
    private static final AtomicLong NEXT_DECAL_ID = new AtomicLong(CONTRAPTION_DECAL_PREFIX + 1L);

    private static final Map<BlockState, double[]> SHELL_LOCAL_BOUNDS = new ConcurrentHashMap<>();

    private record CutMaskCache(long stickerRev, int blockCount, Set<BlockPos> cutNonShellBlocks) {}
    private static final Map<Contraption, CutMaskCache> CUT_MASK_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class ContraptionEntry {
        long shellGroupId;
        long cutGroupId;
        long decalGroupId;
        String cutGeomId;
        String decalGeomId;
        CompiledMesh cutHostMesh;
        CompiledMesh decalHostMesh;
        StructureRevision shellRevision;
        StructureRevision cutRevision;
        StructureRevision decalRevision;
        long contentSig = Long.MIN_VALUE;
        boolean decalsReady = true;
    }

    private static final Map<Contraption, ContraptionEntry> ACTIVE = new IdentityHashMap<>();

    private CreateContraptionGroups() {}

    /**
     * Filter callback for ClientContraption.getRenderedBlocks() and CreateContraptionCasters:
     * returns true for every contraption block rendered directly by HexieRenderEngine.
     */
    static boolean shouldHideFromContraptionMesh(Object contraptionObj, BlockPos localPos, BlockState state) {
        if (state == null || state.isAir()) return false;
        if (HexieRendererAdapter.isShellBlock(state.getBlock())
                && HexieRendererAdapter.shellModelKey(state) != null) {
            return true;
        }
        if (!(contraptionObj instanceof Contraption contraption)) return false;
        return isWindowCutNonShellBlock(contraption, localPos);
    }

    private static boolean isWindowCutNonShellBlock(Contraption contraption, BlockPos localPos) {
        if (!(contraption instanceof ContraptionStickerHost host)) return false;
        List<StickerEntry> stickers = host.hexie$stickers();
        if (stickers.isEmpty()) return false;
        long rev = host.hexie$stickerRevision();
        int blockCount = contraption.getBlocks().size();
        CutMaskCache cached = CUT_MASK_CACHE.get(contraption);
        if (cached == null || cached.stickerRev != rev || cached.blockCount != blockCount) {
            Set<BlockPos> cutSet = computeCutNonShellBlocks(contraption.getBlocks(), stickers);
            cached = new CutMaskCache(rev, blockCount, cutSet);
            CUT_MASK_CACHE.put(contraption, cached);
        }
        return !cached.cutNonShellBlocks.isEmpty() && cached.cutNonShellBlocks.contains(localPos);
    }

    private static Set<BlockPos> computeCutNonShellBlocks(Map<BlockPos, StructureBlockInfo> blocks,
                                                          List<StickerEntry> stickers) {
        if (blocks == null || blocks.isEmpty() || stickers.isEmpty()) return Set.of();
        var shellBounds = collectShellOwnerBounds(blocks);
        Set<BlockPos> cut = null;
        for (StickerEntry e : stickers) {
            if (e.mode() != StickerMode.WINDOW) continue;
            for (BlockPos raw : e.affectedPositions()) {
                BlockPos target = resolveNonShellCutTarget(blocks, raw, shellBounds);
                if (target != null) {
                    if (cut == null) cut = new HashSet<>();
                    cut.add(target);
                }
            }
        }
        return cut == null ? Set.of() : Set.copyOf(cut);
    }

    private record ShellOwnerBounds(BlockPos pos, String modelKey, double[] aabb) {
        boolean coversCell(BlockPos cell) {
            double cx0 = cell.getX() - 0.15, cy0 = cell.getY() - 0.15, cz0 = cell.getZ() - 0.15;
            double cx1 = cell.getX() + 1.15, cy1 = cell.getY() + 1.15, cz1 = cell.getZ() + 1.15;
            return pos.getX() + aabb[3] >= cx0 && pos.getX() + aabb[0] <= cx1
                    && pos.getY() + aabb[4] >= cy0 && pos.getY() + aabb[1] <= cy1
                    && pos.getZ() + aabb[5] >= cz0 && pos.getZ() + aabb[2] <= cz1;
        }
    }

    private static List<ShellOwnerBounds> collectShellOwnerBounds(Map<BlockPos, StructureBlockInfo> blocks) {
        List<ShellOwnerBounds> list = null;
        for (var info : blocks.values()) {
            BlockState state = info.state();
            if (state == null || state.isAir()) continue;
            String key = HexieRendererAdapter.shellModelKey(state);
            if (key == null) continue;
            double[] aabb = shellBoundsForState(state);
            if (aabb == null) continue;
            if (list == null) list = new ArrayList<>();
            list.add(new ShellOwnerBounds(info.pos().immutable(), key, aabb));
        }
        return list == null ? List.of() : list;
    }

    private static double[] shellBoundsForState(BlockState state) {
        return SHELL_LOCAL_BOUNDS.computeIfAbsent(state, st -> {
            CompiledMesh cm = HexieRendererAdapter.compiledShellModel(st);
            if (cm == null || cm.isEmpty()) return new double[]{0, 0, 0, 1, 1, 1};
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            for (var t : cm.triangles()) {
                for (Vec p : new Vec[]{t.a().pos(), t.b().pos(), t.c().pos()}) {
                    if (p.x() < minX) minX = p.x();
                    if (p.y() < minY) minY = p.y();
                    if (p.z() < minZ) minZ = p.z();
                    if (p.x() > maxX) maxX = p.x();
                    if (p.y() > maxY) maxY = p.y();
                    if (p.z() > maxZ) maxZ = p.z();
                }
            }
            return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
        });
    }

    private static BlockPos resolveNonShellCutTarget(Map<BlockPos, StructureBlockInfo> blocks,
                                                     BlockPos raw,
                                                     List<ShellOwnerBounds> shellBounds) {
        StructureBlockInfo info = blocks.get(raw);
        if (info == null || info.state() == null || info.state().isAir()) return null;
        BlockState state = info.state();
        if (HexieRendererAdapter.isShellBlock(state.getBlock())) return null;
        if (state.getRenderShape() != RenderShape.INVISIBLE) return raw.immutable();
        // Invisible member cell: if owned by a StaticShell, the Shell Group handles it (not non-shell cut).
        for (var sb : shellBounds) {
            if (sb.coversCell(raw)) return null;
        }
        // Otherwise locate a non-StaticShell multi-block root below (e.g. tall 25-series panel).
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos below = raw.below(dy);
            StructureBlockInfo bInfo = blocks.get(below);
            if (bInfo != null && bInfo.state() != null && !bInfo.state().isAir()
                    && bInfo.state().getRenderShape() != RenderShape.INVISIBLE
                    && !HexieRendererAdapter.isShellBlock(bInfo.state().getBlock())) {
                return below.immutable();
            }
        }
        return null;
    }

    static void collectHostFrame(ClientLevel level, HexieRenderEngine engine,
                                 float partialTick, List<GroupPose> poses) {
        if (level == null || engine == null) return;
        var seen = Collections.newSetFromMap(new IdentityHashMap<Contraption, Boolean>());

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof AbstractContraptionEntity ace) || !ace.isAlive()) continue;
            Contraption contraption = ace.getContraption();
            if (contraption == null || contraption.getBlocks().isEmpty()) continue;

            List<StickerEntry> stickers = (contraption instanceof ContraptionStickerHost host)
                    ? host.hexie$stickers() : List.of();
            long stickerRev = (contraption instanceof ContraptionStickerHost host)
                    ? host.hexie$stickerRevision() : 0L;

            ContraptionEntry entry = ACTIVE.get(contraption);
            long sig = computeContentSignature(contraption, stickerRev);
            boolean needsBuild = entry == null || entry.contentSig != sig || !entry.decalsReady;

            if (needsBuild) {
                var blocks = contraption.getBlocks();
                var shells = collectShellOwnerBounds(blocks);
                if (shells.isEmpty() && stickers.isEmpty()) {
                    if (entry != null) {
                        evictEntry(engine, entry);
                        ACTIVE.remove(contraption);
                    }
                    continue;
                }
                if (entry == null) {
                    entry = new ContraptionEntry();
                    ACTIVE.put(contraption, entry);
                }
                rebuildContraptionGroups(engine, contraption, blocks, shells, stickers, entry);
                entry.contentSig = sig;
            }

            if (entry.shellGroupId == 0L && entry.cutGroupId == 0L && entry.decalGroupId == 0L) {
                continue;
            }
            seen.add(contraption);

            // Auto-restore if engine.clear() ran (e.g. /hexie_render clear or resource reload)
            ensureRegistered(engine, entry);

            Pose livePose = computeLivePose(ace, partialTick);
            if (entry.shellGroupId != 0L) {
                poses.add(new GroupPose(entry.shellGroupId, GroupPose.ROOT, livePose, Optional.empty()));
            }
            if (entry.cutGroupId != 0L) {
                poses.add(new GroupPose(entry.cutGroupId, GroupPose.ROOT, livePose, Optional.empty()));
            }
            if (entry.decalGroupId != 0L) {
                poses.add(new GroupPose(entry.decalGroupId, GroupPose.ROOT, livePose, Optional.empty()));
            }
        }

        // Evict groups for removed/unloaded contraptions
        if (!ACTIVE.isEmpty()) {
            var it = ACTIVE.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (!seen.contains(e.getKey())) {
                    evictEntry(engine, e.getValue());
                    it.remove();
                }
            }
        }
    }

    private static long computeContentSignature(Contraption contraption, long stickerRev) {
        long h = 17L;
        h = h * 31L + contraption.getBlocks().size();
        h = h * 31L + stickerRev;
        return h;
    }

    private static void rebuildContraptionGroups(HexieRenderEngine engine,
                                                 Contraption contraption,
                                                 Map<BlockPos, StructureBlockInfo> blocks,
                                                 List<ShellOwnerBounds> shells,
                                                 List<StickerEntry> stickers,
                                                 ContraptionEntry entry) {
        RenderBridge bridge = ClientRenderState.bridge();
        UUID carriageId = contraption.entity != null
                ? contraption.entity.getUUID()
                : new UUID(0L, System.identityHashCode(contraption));
        var hostId = new SurfaceHostId.Carriage(carriageId, new Cell(0, 0, 0));

        var shellWindows = new ArrayList<StickerEntry>();
        var cutBlocks = new LinkedHashSet<BlockPos>();
        var cutWindows = new LinkedHashSet<StickerEntry>();
        var decals = new ArrayList<StickerEntry>();

        for (StickerEntry s : stickers) {
            if (s.mode() == StickerMode.WINDOW) {
                boolean hitsShell = false;
                boolean hitsNonShell = false;
                for (BlockPos raw : s.affectedPositions()) {
                    for (var sb : shells) {
                        if (sb.coversCell(raw)) {
                            hitsShell = true;
                            break;
                        }
                    }
                    BlockPos nonShell = resolveNonShellCutTarget(blocks, raw, shells);
                    if (nonShell != null) {
                        cutBlocks.add(nonShell);
                        hitsNonShell = true;
                    }
                }
                if (hitsShell) shellWindows.add(s);
                if (hitsNonShell) cutWindows.add(s);
            } else if (s.mode() == StickerMode.DECAL && s.decal().hasImage()) {
                decals.add(s);
            }
        }

        // 1. StaticShell Group (ALL CRH1/CRH2 shells in this contraption + any WINDOW cuts touching them)
        if (!shells.isEmpty()) {
            var modelRefs = new ArrayList<ModelRef>(shells.size());
            for (var sb : shells) {
                CompiledMesh cached = HexieRendererAdapter.compiledShellModelByKey(sb.modelKey);
                if (cached == null || cached.isEmpty()) continue;
                engine.registerCompiledModel(sb.modelKey, cached);
                Pose local = new Pose(new Vec(sb.pos.getX(), sb.pos.getY(), sb.pos.getZ()), 0, 0, 0, 1);
                modelRefs.add(new ModelRef(sb.modelKey, "atlas:blocks", local));
            }
            if (!modelRefs.isEmpty()) {
                long gid = entry.shellGroupId != 0L ? entry.shellGroupId : NEXT_SHELL_ID.getAndIncrement();
                var openings = buildWindowOpenings(modelRefs.getFirst(), shellWindows);
                StructureRevision rev = new StructureRevision(
                        gid, HexieRendererAdapter.nextRevision(),
                        hostId,
                        modelRefs, openings);
                bridge.structure(rev);
                entry.shellGroupId = gid;
                entry.shellRevision = rev;
            } else {
                clearShellGroup(engine, entry);
            }
        } else {
            clearShellGroup(engine, entry);
        }

        // 2. Non-StaticShell Cut Group (25-series / ordinary blocks cut by contraption WINDOW stickers)
        if (!cutBlocks.isEmpty() && !cutWindows.isEmpty()) {
            var cutAxes = EnumSet.noneOf(Direction.Axis.class);
            for (StickerEntry w : cutWindows) cutAxes.add(w.facing().getAxis());
            CompiledMesh hostMesh = BakedModelMeshCompiler.compileContraptionBlocksForCut(blocks, cutBlocks, cutAxes);
            if (!hostMesh.isEmpty()) {
                long gid = entry.cutGroupId != 0L ? entry.cutGroupId : NEXT_CUT_ID.getAndIncrement();
                String geomId = "hexie:contraption_cut_" + Long.toUnsignedString(gid, 16);
                ModelRef hostRef = new ModelRef(geomId, "atlas:blocks", Pose.IDENTITY);
                var openings = buildWindowOpenings(hostRef, cutWindows);
                engine.registerCompiledModel(geomId, hostMesh);
                StructureRevision rev = new StructureRevision(
                        gid, HexieRendererAdapter.nextRevision(),
                        hostId,
                        List.of(hostRef), openings);
                bridge.structure(rev);
                entry.cutGroupId = gid;
                entry.cutGeomId = geomId;
                entry.cutHostMesh = hostMesh;
                entry.cutRevision = rev;
            } else {
                clearCutGroup(engine, entry);
            }
        } else {
            clearCutGroup(engine, entry);
        }

        // 3. Decal Overlay Group (all contraption DECAL stickers projected onto local shells/blocks)
        if (!decals.isEmpty()) {
            var sampleBlocks = new LinkedHashSet<BlockPos>();
            for (StickerEntry d : decals) {
                Vec3 uAxis = StickerPlaneMath.axisU(d.facing());
                BlockPos uStep = new BlockPos((int) Math.round(uAxis.x), (int) Math.round(uAxis.y), (int) Math.round(uAxis.z));
                Direction behind = d.facing().getOpposite();
                for (BlockPos bp : d.affectedPositions()) {
                    sampleBlocks.add(bp);
                    sampleBlocks.add(bp.relative(behind));
                    sampleBlocks.add(bp.above());
                    sampleBlocks.add(bp.below());
                    sampleBlocks.add(bp.below(2));
                    if (!uStep.equals(BlockPos.ZERO)) {
                        sampleBlocks.add(bp.offset(uStep));
                        sampleBlocks.add(bp.subtract(uStep));
                    }
                    for (var sb : shells) {
                        if (sb.coversCell(bp)) sampleBlocks.add(sb.pos);
                    }
                }
            }
            CompiledMesh hostMesh = BakedModelMeshCompiler.compileContraptionBlocks(blocks, sampleBlocks);
            if (!hostMesh.isEmpty()) {
                long gid = entry.decalGroupId != 0L ? entry.decalGroupId : NEXT_DECAL_ID.getAndIncrement();
                String geomId = "hexie:contraption_decal_" + Long.toUnsignedString(gid, 16);
                ModelRef hostRef = new ModelRef(geomId, "atlas:blocks", Pose.IDENTITY);
                var openings = new ArrayList<OpeningRequest>(decals.size());
                boolean allReady = true;
                for (StickerEntry e : decals) {
                    var d = e.decal();
                    if (!HexieRendererAdapter.ensureDecalImageRegistered(engine, d.imageHash())) {
                        allReady = false;
                        continue;
                    }
                    Vec3 fc = StickerPlaneMath.faceCenter(e.anchorPos(), e.facing());
                    Vec3 inw = StickerPlaneMath.outward(e.facing()).scale(-1.0);
                    Pose placement = new Pose(new Vec(fc.x, fc.y, fc.z), 0, 0, 0, 1);
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
                entry.decalsReady = allReady;
                if (!openings.isEmpty()) {
                    engine.registerCompiledModel(geomId, hostMesh);
                    StructureRevision rev = new StructureRevision(
                            gid, HexieRendererAdapter.nextRevision(),
                            hostId,
                            List.of(hostRef), openings);
                    bridge.structure(rev);
                    entry.decalGroupId = gid;
                    entry.decalGeomId = geomId;
                    entry.decalHostMesh = hostMesh;
                    entry.decalRevision = rev;
                } else {
                    clearDecalGroup(engine, entry);
                }
            } else {
                entry.decalsReady = true;
                clearDecalGroup(engine, entry);
            }
        } else {
            entry.decalsReady = true;
            clearDecalGroup(engine, entry);
        }
    }

    private static List<OpeningRequest> buildWindowOpenings(ModelRef targetRef, Collection<StickerEntry> windows) {
        if (windows.isEmpty()) return List.of();
        var openings = new ArrayList<OpeningRequest>(windows.size());
        for (StickerEntry win : windows) {
            if (win.mode() != StickerMode.WINDOW) continue;
            var w = win.window();
            Vec3 fc = StickerPlaneMath.faceCenter(win.anchorPos(), win.facing());
            Vec3 inw = StickerPlaneMath.outward(win.facing()).scale(-1.0);
            Pose placement = new Pose(new Vec(fc.x, fc.y, fc.z), 0, 0, 0, 1);
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
        return openings;
    }

    private static void ensureRegistered(HexieRenderEngine engine, ContraptionEntry entry) {
        if (entry.shellGroupId != 0L && !engine.hasGroup(entry.shellGroupId) && entry.shellRevision != null) {
            for (var mr : entry.shellRevision.models()) {
                CompiledMesh cm = HexieRendererAdapter.compiledShellModelByKey(mr.geometryId());
                if (cm != null) engine.registerCompiledModel(mr.geometryId(), cm);
            }
            engine.structure(entry.shellRevision);
        }
        if (entry.cutGroupId != 0L && !engine.hasGroup(entry.cutGroupId) && entry.cutRevision != null) {
            if (entry.cutGeomId != null && entry.cutHostMesh != null) {
                engine.registerCompiledModel(entry.cutGeomId, entry.cutHostMesh);
            }
            engine.structure(entry.cutRevision);
        }
        if (entry.decalGroupId != 0L && !engine.hasGroup(entry.decalGroupId) && entry.decalRevision != null) {
            if (entry.decalGeomId != null && entry.decalHostMesh != null) {
                engine.registerCompiledModel(entry.decalGeomId, entry.decalHostMesh);
            }
            engine.structure(entry.decalRevision);
        }
    }

    private static Pose computeLivePose(AbstractContraptionEntity ace, float partialTick) {
        Vec3 o = toWorldInterpolated(ace, Vec3.ZERO, partialTick);
        Vec3 bx = toWorldInterpolated(ace, new Vec3(1, 0, 0), partialTick).subtract(o);
        Vec3 by = toWorldInterpolated(ace, new Vec3(0, 1, 0), partialTick).subtract(o);
        Vec3 bz = toWorldInterpolated(ace, new Vec3(0, 0, 1), partialTick).subtract(o);
        double lx = bx.length(), ly = by.length(), lz = bz.length();
        if (lx < 1e-6 || ly < 1e-6 || lz < 1e-6) {
            return new Pose(new Vec(o.x, o.y, o.z), 0, 0, 0, 1);
        }
        bx = bx.scale(1.0 / lx);
        by = by.scale(1.0 / ly);
        bz = bz.scale(1.0 / lz);
        Matrix3d mat = new Matrix3d();
        mat.setColumn(0, bx.x, bx.y, bx.z);
        mat.setColumn(1, by.x, by.y, by.z);
        mat.setColumn(2, bz.x, bz.y, bz.z);
        Quaterniond q = new Quaterniond().setFromNormalized(mat);
        return new Pose(new Vec(o.x, o.y, o.z), q.x, q.y, q.z, q.w);
    }

    private static Vec3 toWorldInterpolated(AbstractContraptionEntity entity, Vec3 local, float partial) {
        Vec3 storage = entity instanceof SnapshotCarriageView car
                ? HexieBridges.createPlayer().frame(car, partial).world(local)
                : entity.toGlobalVector(local, partial);
        return HexieBridges.sable().render(entity.level(), entity.blockPosition(), partial).toWorld(storage);
    }

    private static void clearShellGroup(HexieRenderEngine engine, ContraptionEntry entry) {
        if (entry.shellGroupId != 0L) {
            if (engine != null) engine.removeGroup(entry.shellGroupId);
            entry.shellGroupId = 0L;
            entry.shellRevision = null;
        }
    }

    private static void clearCutGroup(HexieRenderEngine engine, ContraptionEntry entry) {
        if (entry.cutGroupId != 0L) {
            if (engine != null) engine.removeGroup(entry.cutGroupId);
            entry.cutGroupId = 0L;
            entry.cutRevision = null;
        }
        if (entry.cutGeomId != null) {
            if (engine != null) engine.unregisterModel(entry.cutGeomId);
            entry.cutGeomId = null;
            entry.cutHostMesh = null;
        }
    }

    private static void clearDecalGroup(HexieRenderEngine engine, ContraptionEntry entry) {
        if (entry.decalGroupId != 0L) {
            if (engine != null) engine.removeGroup(entry.decalGroupId);
            entry.decalGroupId = 0L;
            entry.decalRevision = null;
        }
        if (entry.decalGeomId != null) {
            if (engine != null) engine.unregisterModel(entry.decalGeomId);
            entry.decalGeomId = null;
            entry.decalHostMesh = null;
        }
    }

    private static void evictEntry(HexieRenderEngine engine, ContraptionEntry entry) {
        clearShellGroup(engine, entry);
        clearCutGroup(engine, entry);
        clearDecalGroup(engine, entry);
    }

    static void onLevelUnload(HexieRenderEngine engine) {
        for (var entry : ACTIVE.values()) {
            evictEntry(engine, entry);
        }
        ACTIVE.clear();
        CUT_MASK_CACHE.clear();
    }

    static void clearShellBoundsCache() {
        SHELL_LOCAL_BOUNDS.clear();
    }
}
