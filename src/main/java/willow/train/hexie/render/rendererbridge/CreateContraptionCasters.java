package willow.train.hexie.render.rendererbridge;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import dev.hexie.renderer.LightState;
import dev.hexie.renderer.Triangle;
import dev.hexie.renderer.Vec;
import dev.hexie.renderer.mc.ClientRenderState;
import dev.hexie.renderer.mc.ForwardLight;
import dev.hexie.renderer.parse.CompiledMesh;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import willow.train.hexie.bridge.sticker.ContraptionStickerHost;
import willow.train.hexie.render.init.CarriageInteractionCoordinates;

/**
 * Provides real-time moving opaque shadow-casting triangles for assembled Create trains & contraptions
 * (CarriageContraptionEntity, SnapshotCarriageEntity, etc.) with per-Contraption local mesh caching,
 * 3D greedy solid-block merging, and alpha-pruned CRH1/CRH2 carriage shell meshes so assembled train
 * walls/roofs/floors cast crisp moving shadows while windows transmit light cleanly.
 */
final class CreateContraptionCasters {
    private static final double MAX_RANGE_SQ = 160.0 * 160.0;
    private static final int MAX_LOCAL_TRIS_PER_CONTRAPTION = 24576;

    private record LocalTri(double ax, double ay, double az,
                            double bx, double by, double bz,
                            double cx, double cy, double cz) {}

    private record LocalLamp(double x, double y, double z, LightState source, boolean ceilingDown) {}

    private record LocalAssembly(List<LocalTri> tris, float[] packedVerts,
                                 List<LocalLamp> lamps, long rev, long stickerRev, int blockCount) {
        static final LocalAssembly EMPTY = new LocalAssembly(List.of(), new float[0], List.of(), 0L, 0L, 0);
    }

    private record LampCluster(Block block, int cx, int cy, int cz) {}

    private static final Map<Contraption, LocalAssembly> LOCAL_CACHE = new WeakHashMap<>();
    private static long assemblyRevSeq = 1L;

    private CreateContraptionCasters() {}

    /**
     * Zero-CPU-transform GPU shadow mesh provider: supplies each Contraption's cached local-space float[] VBO
     * and its 4x4 column-major world affine matrix so GpuShadowMap renders moving trains in 1 draw call at 160m range!
     */
    static void collectDynamicMeshes(Object levelObj, Vec focus, float partialTick,
                                     List<ClientRenderState.DynamicShadowMesh> outMeshes,
                                     List<ForwardLight.Lamp> outLamps) {
        if (!(levelObj instanceof ClientLevel level)) return;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof AbstractContraptionEntity ace) || !ace.isAlive()) continue;
            Contraption contraption = ace.getContraption();
            if (contraption == null || contraption.getBlocks().isEmpty()) continue;
            AABB bb = ace.getBoundingBoxForCulling();
            if (bb.distanceToSqr(new Vec3(focus.x(), focus.y(), focus.z())) > MAX_RANGE_SQ) continue;

            LocalAssembly local = getOrBuildLocalAssembly(contraption);
            if (local.packedVerts.length == 0 && local.lamps.isEmpty()) continue;

            Vec3 o = CarriageInteractionCoordinates.toWorld(ace, Vec3.ZERO);
            Vec3 bx = CarriageInteractionCoordinates.toWorld(ace, new Vec3(1, 0, 0)).subtract(o);
            Vec3 by = CarriageInteractionCoordinates.toWorld(ace, new Vec3(0, 1, 0)).subtract(o);
            Vec3 bz = CarriageInteractionCoordinates.toWorld(ace, new Vec3(0, 0, 1)).subtract(o);

            if (local.packedVerts.length >= 9) {
                // Column-major 4x4 affine matrix for OpenGL uniform mat4
                float[] mat = new float[]{
                        (float) bx.x, (float) bx.y, (float) bx.z, 0f,
                        (float) by.x, (float) by.y, (float) by.z, 0f,
                        (float) bz.x, (float) bz.y, (float) bz.z, 0f,
                        (float) o.x,  (float) o.y,  (float) o.z,  1f
                };
                outMeshes.add(new ClientRenderState.DynamicShadowMesh(
                        System.identityHashCode(contraption), local.rev, local.packedVerts, mat));
            }
            for (var ll : local.lamps) {
                Vec wp = xform(o, bx, by, bz, ll.x, ll.y, ll.z);
                outLamps.add(new ForwardLight.Lamp(wp, ll.source,
                        ForwardLight.TARGET_ALL | (ll.ceilingDown ? ForwardLight.TARGET_CEILING_DOWN : 0)));
            }
        }
    }

    static long collect(Object levelObj, Vec focus, float partialTick, int maxTriangles,
                        List<ForwardLight.CasterTri> outTris, List<ForwardLight.Lamp> outLamps) {
        if (!(levelObj instanceof ClientLevel level) || maxTriangles <= 0) return 0L;
        record Active(AbstractContraptionEntity entity, Contraption contraption, double dist2) {}
        var active = new ArrayList<Active>();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof AbstractContraptionEntity ace)) continue;
            if (!ace.isAlive()) continue;
            Contraption contraption = ace.getContraption();
            if (contraption == null || contraption.getBlocks().isEmpty()) continue;
            AABB bb = ace.getBoundingBoxForCulling();
            double d2 = bb.distanceToSqr(new Vec3(focus.x(), focus.y(), focus.z()));
            if (d2 > MAX_RANGE_SQ) continue;
            active.add(new Active(ace, contraption, d2));
        }
        if (active.isEmpty()) return 0L;
        active.sort(Comparator.comparingDouble(Active::dist2));

        long sig = 31L;
        long triIdBase = 0x54524E00_00000000L; // "TRN\0"
        long seq = 0;

        for (var a : active) {
            if (outTris.size() >= maxTriangles) break;
            LocalAssembly local = getOrBuildLocalAssembly(a.contraption);
            if (local.tris.isEmpty() && local.lamps.isEmpty()) continue;

            // Evaluate exact world affine basis (handles Create curves/slopes, SnapshotCarriageView, and Sable plots).
            Vec3 o = CarriageInteractionCoordinates.toWorld(a.entity, Vec3.ZERO);
            Vec3 bx = CarriageInteractionCoordinates.toWorld(a.entity, new Vec3(1, 0, 0)).subtract(o);
            Vec3 by = CarriageInteractionCoordinates.toWorld(a.entity, new Vec3(0, 1, 0)).subtract(o);
            Vec3 bz = CarriageInteractionCoordinates.toWorld(a.entity, new Vec3(0, 0, 1)).subtract(o);

            sig = sig * 31L + System.identityHashCode(a.contraption);
            sig = sig * 31L + local.tris.size();
            sig = sig * 31L + local.lamps.size();
            sig = mixCoord(sig, o.x); sig = mixCoord(sig, o.y); sig = mixCoord(sig, o.z);
            sig = mixCoord(sig, bx.x); sig = mixCoord(sig, bx.y); sig = mixCoord(sig, bx.z);
            sig = mixCoord(sig, by.x); sig = mixCoord(sig, by.y); sig = mixCoord(sig, by.z);
            sig = mixCoord(sig, bz.x); sig = mixCoord(sig, bz.y); sig = mixCoord(sig, bz.z);

            for (var ll : local.lamps) {
                Vec wp = xform(o, bx, by, bz, ll.x, ll.y, ll.z);
                outLamps.add(new ForwardLight.Lamp(wp, ll.source,
                        ForwardLight.TARGET_ALL | (ll.ceilingDown ? ForwardLight.TARGET_CEILING_DOWN : 0)));
            }
            for (var lt : local.tris) {
                if (outTris.size() >= maxTriangles) break;
                Vec wa = xform(o, bx, by, bz, lt.ax, lt.ay, lt.az);
                Vec wb = xform(o, bx, by, bz, lt.bx, lt.by, lt.bz);
                Vec wc = xform(o, bx, by, bz, lt.cx, lt.cy, lt.cz);
                outTris.add(new ForwardLight.CasterTri(new Triangle(triIdBase + (seq++), wa, wb, wc), 0));
            }
        }
        return sig;
    }

    private static long mixCoord(long h, double v) {
        return h * 31L + Math.round(v * 256.0);
    }

    private static Vec xform(Vec3 o, Vec3 bx, Vec3 by, Vec3 bz, double x, double y, double z) {
        return new Vec(
                o.x + bx.x * x + by.x * y + bz.x * z,
                o.y + bx.y * x + by.y * y + bz.y * z,
                o.z + bx.z * x + by.z * y + bz.z * z);
    }

    private static LocalAssembly getOrBuildLocalAssembly(Contraption contraption) {
        long sRev = (contraption instanceof ContraptionStickerHost host) ? host.hexie$stickerRevision() : 0L;
        int bCount = contraption.getBlocks().size();
        LocalAssembly cached = LOCAL_CACHE.get(contraption);
        if (cached != null && cached.stickerRev == sRev && cached.blockCount == bCount) {
            return cached;
        }
        LocalAssembly built = buildLocalAssembly(contraption, sRev, bCount);
        LOCAL_CACHE.put(contraption, built);
        return built;
    }

    private static LocalAssembly buildLocalAssembly(Contraption contraption, long stickerRev, int blockCount) {
        var blocks = contraption.getBlocks();
        if (blocks.isEmpty()) return LocalAssembly.EMPTY;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        var fullCells = new ArrayList<BlockPos>();
        var partialBoxes = new ArrayList<double[]>();
        var tris = new ArrayList<LocalTri>(256);
        var lampClusters = new LinkedHashMap<LampCluster, LocalLamp>();

        for (var info : blocks.values()) {
            BlockState state = info.state();
            if (state == null || state.isAir()) continue;
            Block block = state.getBlock();
            BlockPos pos = info.pos();

            // 0. Collect moving carriage interior lamps / torches / headlights, and omit non-ceiling emitters
            // from triangle occlusion so light shines freely out of glowstone / sea lanterns / torches!
            int emission = state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (emission > 0) {
                double ceilingY = HexieRendererAdapter.isHeXieBlock(block)
                        ? BakedModelMeshCompiler.ceilingEmitterOffsetY(state) : Double.NaN;
                boolean isCeilingRoof = !Double.isNaN(ceilingY);
                collectLocalLamp(state, pos, emission, isCeilingRoof, ceilingY, lampClusters);
                if (!isCeilingRoof) continue;
            }

            if (state.getRenderShape() == RenderShape.INVISIBLE) continue;

            // Blocks owned by CreateContraptionGroups (StaticShells + window-cut blocks) already cast exact cut shadows
            // straight from HexieRenderEngine scene.sections() at the contraption's live Pose!
            if (CreateContraptionGroups.shouldHideFromContraptionMesh(contraption, pos, state)) continue;

            // 1. StaticShell carriage parts (CRH1/CRH2 panels, floors, roofs, heads): exact opaque OBJ mesh!
            if (HexieRendererAdapter.isShellBlock(block)) {
                CompiledMesh cm = HexieRendererAdapter.compiledShellModel(state);
                if (cm != null && !cm.isEmpty()) {
                    double ox = pos.getX(), oy = pos.getY(), oz = pos.getZ();
                    for (var t : cm.triangles()) {
                        if (t.translucent()) continue;
                        if (tris.size() >= MAX_LOCAL_TRIS_PER_CONTRAPTION) break;
                        tris.add(new LocalTri(
                                ox + t.a().pos().x(), oy + t.a().pos().y(), oz + t.a().pos().z(),
                                ox + t.b().pos().x(), oy + t.b().pos().y(), oz + t.b().pos().z(),
                                ox + t.c().pos().x(), oy + t.c().pos().y(), oz + t.c().pos().z()));
                    }
                }
                continue;
            }

            // 1b. All non-full-cube HeXie blocks (roofs, AC units, windows, doors, end faces, panels, seats, etc.):
            // emit exact cached alpha-pruned BakedModel shadow triangles (zero name whitelists!).
            if (HexieRendererAdapter.usesCachedModelShadow(state)) {
                var shadowTris = BakedModelMeshCompiler.compileOpaqueShadowTris(state);
                double ox = pos.getX(), oy = pos.getY(), oz = pos.getZ();
                for (var t : shadowTris) {
                    if (tris.size() >= MAX_LOCAL_TRIS_PER_CONTRAPTION) break;
                    tris.add(new LocalTri(
                            ox + t.a().pos().x(), oy + t.a().pos().y(), oz + t.a().pos().z(),
                            ox + t.b().pos().x(), oy + t.b().pos().y(), oz + t.b().pos().z(),
                            ox + t.c().pos().x(), oy + t.c().pos().y(), oz + t.c().pos().z()));
                }
                continue;
            }

            // 2. Skip pure vanilla/third-party glass, panes, leaves, and fluids
            if (!state.getFluidState().isEmpty() || block instanceof LeavesBlock) continue;
            if (block instanceof StainedGlassPaneBlock || state.is(Blocks.GLASS_PANE) || state.is(Tags.Blocks.GLASS_PANES)) continue;
            if (block instanceof HalfTransparentBlock || state.is(Tags.Blocks.GLASS_BLOCKS)) {
                if (state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 15) continue;
            }

            // 3. Regular solid or partial block inside the contraption
            VoxelShape shape;
            if (state.canOcclude()) {
                shape = state.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (shape.isEmpty()) shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            } else {
                // Solid-motion carriage panels (e.g. OrdinaryTrainUpperPanelBlock) override getVisualShape to empty
                // for camera comfort, yet still form opaque walls/roofs that must cast shadows.
                if (!state.blocksMotion()
                        && state.getVisualShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).isEmpty()) {
                    continue;
                }
                shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            }
            if (shape.isEmpty()) continue;
            if (Block.isShapeFullBlock(shape)) {
                fullCells.add(pos);
                if (pos.getX() < minX) minX = pos.getX();
                if (pos.getY() < minY) minY = pos.getY();
                if (pos.getZ() < minZ) minZ = pos.getZ();
                if (pos.getX() > maxX) maxX = pos.getX();
                if (pos.getY() > maxY) maxY = pos.getY();
                if (pos.getZ() > maxZ) maxZ = pos.getZ();
            } else {
                for (AABB bb : shape.toAabbs()) {
                    if (bb.getXsize() <= 1e-5 || bb.getYsize() <= 1e-5 || bb.getZsize() <= 1e-5) continue;
                    partialBoxes.add(new double[]{
                            pos.getX() + bb.minX, pos.getY() + bb.minY, pos.getZ() + bb.minZ,
                            pos.getX() + bb.maxX, pos.getY() + bb.maxY, pos.getZ() + bb.maxZ});
                }
            }
        }

        // Greedy 3D merge for full solid contraption blocks -> compact box-face triangles
        if (!fullCells.isEmpty()) {
            int sx = maxX - minX + 1, sy = maxY - minY + 1, sz = maxZ - minZ + 1;
            long vol = (long) sx * sy * sz;
            if (vol > 0 && vol <= 262144L) {
                boolean[] occ = new boolean[(int) vol];
                for (BlockPos p : fullCells) {
                    occ[((p.getY() - minY) * sz + (p.getZ() - minZ)) * sx + (p.getX() - minX)] = true;
                }
                for (int y = 0; y < sy && tris.size() + 12 <= MAX_LOCAL_TRIS_PER_CONTRAPTION; y++) {
                    for (int z = 0; z < sz && tris.size() + 12 <= MAX_LOCAL_TRIS_PER_CONTRAPTION; z++) {
                        for (int x = 0; x < sx && tris.size() + 12 <= MAX_LOCAL_TRIS_PER_CONTRAPTION; x++) {
                            if (!occ[(y * sz + z) * sx + x]) continue;
                            int wx = 1;
                            while (x + wx < sx && occ[(y * sz + z) * sx + x + wx]) wx++;
                            int wz = 1;
                            outerZ:
                            while (z + wz < sz) {
                                int r = (y * sz + z + wz) * sx + x;
                                for (int dx = 0; dx < wx; dx++) if (!occ[r + dx]) break outerZ;
                                wz++;
                            }
                            int wy = 1;
                            outerY:
                            while (y + wy < sy) {
                                for (int dz = 0; dz < wz; dz++) {
                                    int r = ((y + wy) * sz + z + dz) * sx + x;
                                    for (int dx = 0; dx < wx; dx++) if (!occ[r + dx]) break outerY;
                                }
                                wy++;
                            }
                            for (int dy = 0; dy < wy; dy++) {
                                for (int dz = 0; dz < wz; dz++) {
                                    int r = ((y + dy) * sz + z + dz) * sx + x;
                                    for (int dx = 0; dx < wx; dx++) occ[r + dx] = false;
                                }
                            }
                            emitBoxTris(minX + x, minY + y, minZ + z,
                                    minX + x + wx, minY + y + wy, minZ + z + wz, tris);
                        }
                    }
                }
            }
        }

        for (double[] b : mergePartialBoxes(partialBoxes)) {
            if (tris.size() + 12 > MAX_LOCAL_TRIS_PER_CONTRAPTION) break;
            emitBoxTris(b[0], b[1], b[2], b[3], b[4], b[5], tris);
        }
        float[] packed = new float[tris.size() * 9];
        int k = 0;
        for (var lt : tris) {
            packed[k++] = (float) lt.ax; packed[k++] = (float) lt.ay; packed[k++] = (float) lt.az;
            packed[k++] = (float) lt.bx; packed[k++] = (float) lt.by; packed[k++] = (float) lt.bz;
            packed[k++] = (float) lt.cx; packed[k++] = (float) lt.cy; packed[k++] = (float) lt.cz;
        }
        return new LocalAssembly(
                tris.isEmpty() ? List.of() : List.copyOf(tris),
                packed,
                lampClusters.isEmpty() ? List.of() : List.copyOf(lampClusters.values()),
                assemblyRevSeq++,
                stickerRev,
                blockCount);
    }

    /** Fast O(N log N) exact axis-aligned merge along X -> Z -> Y so long carriage roofs/walls collapse 10-20x! */
    private static List<double[]> mergePartialBoxes(List<double[]> input) {
        if (input.size() <= 1) return input;
        List<double[]> cur = mergeAlongAxis(input, 0);
        cur = mergeAlongAxis(cur, 2);
        return mergeAlongAxis(cur, 1);
    }

    private static List<double[]> mergeAlongAxis(List<double[]> boxes, int axis) {
        int a1 = (axis + 1) % 3, a2 = (axis + 2) % 3;
        var groups = new LinkedHashMap<String, List<double[]>>();
        for (double[] b : boxes) {
            String key = Math.round(b[a1] * 10000.0) + ":" + Math.round(b[a1 + 3] * 10000.0)
                    + ":" + Math.round(b[a2] * 10000.0) + ":" + Math.round(b[a2 + 3] * 10000.0);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        var out = new ArrayList<double[]>(boxes.size());
        for (var list : groups.values()) {
            if (list.size() == 1) {
                out.add(list.getFirst());
                continue;
            }
            list.sort(Comparator.comparingDouble(b -> b[axis]));
            double[] cur = list.getFirst().clone();
            for (int i = 1; i < list.size(); i++) {
                double[] next = list.get(i);
                if (next[axis] <= cur[axis + 3] + 1e-4) {
                    cur[axis + 3] = Math.max(cur[axis + 3], next[axis + 3]);
                } else {
                    out.add(cur);
                    cur = next.clone();
                }
            }
            out.add(cur);
        }
        return out;
    }

    private static void collectLocalLamp(BlockState state, BlockPos pos, int emission, boolean isCeilingRoof,
                                         double ceilingY, Map<LampCluster, LocalLamp> clusters) {
        // Cluster only directly adjacent 2x2x2 identical blocks (not 4x4x4) so every distinct carriage ceiling lamp shines!
        var key = new LampCluster(state.getBlock(), pos.getX() >> 1, pos.getY() >> 1, pos.getZ() >> 1);
        if (clusters.containsKey(key)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        if (state.getBlock() instanceof TorchBlock) {
            if (state.hasProperty(WallTorchBlock.FACING)) {
                var face = state.getValue(WallTorchBlock.FACING);
                x -= 0.27 * face.getStepX();
                z -= 0.27 * face.getStepZ();
                y = pos.getY() + 0.92;
            } else y = pos.getY() + 0.7;
        } else if (state.getBlock() instanceof LanternBlock) {
            y = pos.getY() + (state.getValue(LanternBlock.HANGING) ? 0.4375 : 0.3125);
        } else if (isCeilingRoof) {
            y = pos.getY() + ceilingY;
        }
        Vec color;
        if (state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.SOUL_LANTERN)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.SOUL_FIRE)) color = new Vec(0.22, 0.72, 1.0);
        else if (state.is(Blocks.SEA_LANTERN) || state.is(Blocks.END_ROD) || state.is(Blocks.BEACON)) color = new Vec(0.78, 0.91, 1.0);
        else if (state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)) color = new Vec(1.0, 0.16, 0.07);
        else if (isCeilingRoof) color = new Vec(1.0, 0.94, 0.84);
        else color = new Vec(1.0, 0.64, 0.30);
        var source = new LightState(0x434C0000_00000000L | (pos.asLong() & 0x0000_FFFF_FFFFFFFFL),
                0, 0, Vec.ZERO, color, 2.8 * emission / 15.0, emission, true, LightState.LightKind.POINT, 0, 0);
        clusters.put(key, new LocalLamp(x, y, z, source, isCeilingRoof));
    }

    private static void emitBoxTris(double x0, double y0, double z0, double x1, double y1, double z1,
                                    List<LocalTri> out) {
        // -Z, +Z, -X, +X, -Y, +Y (2 triangles per face)
        out.add(new LocalTri(x0, y0, z0, x0, y1, z0, x1, y1, z0));
        out.add(new LocalTri(x0, y0, z0, x1, y1, z0, x1, y0, z0));
        out.add(new LocalTri(x0, y0, z1, x1, y1, z1, x0, y1, z1));
        out.add(new LocalTri(x0, y0, z1, x1, y0, z1, x1, y1, z1));
        out.add(new LocalTri(x0, y0, z0, x0, y0, z1, x0, y1, z1));
        out.add(new LocalTri(x0, y0, z0, x0, y1, z1, x0, y1, z0));
        out.add(new LocalTri(x1, y0, z0, x1, y1, z1, x1, y0, z1));
        out.add(new LocalTri(x1, y0, z0, x1, y1, z0, x1, y1, z1));
        out.add(new LocalTri(x0, y0, z0, x1, y0, z1, x0, y0, z1));
        out.add(new LocalTri(x0, y0, z0, x1, y0, z0, x1, y0, z1));
        out.add(new LocalTri(x0, y1, z0, x0, y1, z1, x1, y1, z1));
        out.add(new LocalTri(x0, y1, z0, x1, y1, z1, x1, y1, z0));
    }

    static void clearCache() {
        LOCAL_CACHE.clear();
    }
}
