package willow.train.hexie.render.rendererbridge;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hexie.renderer.Vec;
import dev.hexie.renderer.parse.CompiledMesh;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Converts live Minecraft BakedModels (ordinary blocks, neoforge:obj, and StaticShellModel CRH1/CRH2 OBJ shells)
 * into hexie-renderer CompiledMesh instances preserving exact 3D positions, normals, block-atlas UVs, and render layers.
 */
final class BakedModelMeshCompiler {
    private static final Map<BlockState, List<CompiledMesh.Tri>> OPAQUE_SHADOW_CACHE = new ConcurrentHashMap<>();
    private static final Map<BlockState, Double> CEILING_EMITTER_CACHE = new ConcurrentHashMap<>();
    private static final double MIN_SHADOW_AREA2 = 2.5e-4; // drops tiny <1.5px micro-details (bolts/rivets/trim)
    private static final int MAX_SHADOW_TRIS_PER_STATE = 128;

    private BakedModelMeshCompiler() {}

    static void clearShadowCache() {
        OPAQUE_SHADOW_CACHE.clear();
        CEILING_EMITTER_CACHE.clear();
    }

    /**
     * Pure-geometry check returning the safe local Y offset strictly beneath an opaque upper ceiling lamp plate
     * (maxY >= 0.72, single-sided projected Y area >= 0.42), or Double.NaN if not a ceiling roof emitter.
     * Placing the point light strictly below minOpaqueY prevents the lamp from ever being trapped inside its own housing!
     */
    static double ceilingEmitterOffsetY(BlockState state) {
        if (state == null || state.isAir()) return Double.NaN;
        return CEILING_EMITTER_CACHE.computeIfAbsent(state, st -> {
            if (st.getLightEmission(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) <= 0) {
                return Double.NaN;
            }
            var tris = compileOpaqueShadowTris(st);
            double areaY = 0.0, maxY = 0.0, minY = 1.0;
            for (var t : tris) {
                Vec e1 = t.b().pos().minus(t.a().pos());
                Vec e2 = t.c().pos().minus(t.a().pos());
                Vec cr = e1.cross(e2);
                areaY += Math.abs(cr.y()) * 0.5;
                maxY = Math.max(maxY, Math.max(t.a().pos().y(), Math.max(t.b().pos().y(), t.c().pos().y())));
                minY = Math.min(minY, Math.min(t.a().pos().y(), Math.min(t.b().pos().y(), t.c().pos().y())));
            }
            if (maxY >= 0.72 && (areaY * 0.5) >= 0.42) {
                return Math.clamp(minY - 0.14, 0.05, 0.75);
            }
            return Double.NaN;
        });
    }

    /**
     * Compiles and caches local-space opaque shadow triangles for any non-full-cube HeXie block state directly from
     * its live BakedModel: keeps only texels with alpha >= 224 (so framed windows/doors transmit light cleanly while
     * overhanging roofs, AC units, slanted panels, and arches cast exact shadows), drops sub-pixel micro-details,
     * and deduplicates coincident opposite-winding faces (since GPU ray-triangle intersection is two-sided).
     */
    static List<CompiledMesh.Tri> compileOpaqueShadowTris(BlockState state) {
        if (state == null) return List.of();
        return OPAQUE_SHADOW_CACHE.computeIfAbsent(state, st -> {
            var mc = Minecraft.getInstance();
            if (mc == null || mc.getBlockRenderer() == null) return List.of();
            var model = mc.getBlockRenderer().getBlockModel(st);
            if (model == null) return List.of();
            var rand = RandomSource.create(42L);
            var raw = new ArrayList<CompiledMesh.Tri>(32);
            int faceId = 0;
            var renderTypes = model.getRenderTypes(st, rand, ModelData.EMPTY);
            boolean anyLayer = false;
            for (RenderType layer : renderTypes) {
                anyLayer = true;
                for (int side = -1; side < 6; side++) {
                    rand.setSeed(42L);
                    Direction dir = side < 0 ? null : Direction.values()[side];
                    for (BakedQuad q : model.getQuads(st, dir, rand, ModelData.EMPTY, layer)) {
                        faceId = unpackQuad(q, 0.0, 0.0, 0.0, "atlas:blocks", false, 224, faceId, raw);
                    }
                }
            }
            if (!anyLayer) {
                for (int side = -1; side < 6; side++) {
                    rand.setSeed(42L);
                    Direction dir = side < 0 ? null : Direction.values()[side];
                    for (BakedQuad q : model.getQuads(st, dir, rand, ModelData.EMPTY, null)) {
                        faceId = unpackQuad(q, 0.0, 0.0, 0.0, "atlas:blocks", false, 224, faceId, raw);
                    }
                }
            }
            if (raw.isEmpty()) return List.of();
            var seen = new HashSet<TriKey>(raw.size());
            var dedup = new ArrayList<CompiledMesh.Tri>(raw.size());
            for (var t : raw) {
                if (t.area2() < MIN_SHADOW_AREA2) continue;
                if (seen.add(TriKey.of(t))) dedup.add(t);
            }
            if (dedup.size() > MAX_SHADOW_TRIS_PER_STATE) {
                dedup.sort(Comparator.comparingDouble(CompiledMesh.Tri::area2).reversed());
                return List.copyOf(dedup.subList(0, MAX_SHADOW_TRIS_PER_STATE));
            }
            return dedup.isEmpty() ? List.of() : List.copyOf(dedup);
        });
    }

    private record TriKey(long v0, long v1, long v2) {
        static TriKey of(CompiledMesh.Tri t) {
            long a = quant(t.a().pos());
            long b = quant(t.b().pos());
            long c = quant(t.c().pos());
            if (a > b) { long tmp = a; a = b; b = tmp; }
            if (b > c) { long tmp = b; b = c; c = tmp; }
            if (a > b) { long tmp = a; a = b; b = tmp; }
            return new TriKey(a, b, c);
        }

        private static long quant(Vec v) {
            long x = Math.round(v.x() * 256.0) & 0x1FFFFFL;
            long y = Math.round(v.y() * 256.0) & 0x1FFFFFL;
            long z = Math.round(v.z() * 256.0) & 0x1FFFFFL;
            return (x << 42) | (y << 21) | z;
        }
    }

    /** Compiles a single BakedModel in block-local coordinates for StaticShell registration. */
    static CompiledMesh compileBlockStateModel(BakedModel model, BlockState state) {
        if (model == null || state == null) return CompiledMesh.EMPTY;
        var rand = RandomSource.create(42L);
        var tris = new ArrayList<CompiledMesh.Tri>(64);
        int faceId = 0;

        for (RenderType layer : model.getRenderTypes(state, rand, ModelData.EMPTY)) {
            String mat = materialForRenderType(layer);
            boolean translucent = mat.endsWith(":translucent");
            int alphaThreshold = mat.endsWith(":cutout") ? 128 : 0;
            for (int side = -1; side < 6; side++) {
                rand.setSeed(42L);
                Direction dir = side < 0 ? null : Direction.values()[side];
                for (BakedQuad q : model.getQuads(state, dir, rand, ModelData.EMPTY, layer)) {
                    faceId = unpackQuad(q, 0.0, 0.0, 0.0, mat, translucent, alphaThreshold, faceId, tris);
                }
            }
        }
        return tris.isEmpty() ? CompiledMesh.EMPTY : CompiledMesh.of(tris);
    }

    static CompiledMesh compileWorldBlocks(Level level, Set<BlockPos> positions, Direction facingHint) {
        var mc = Minecraft.getInstance();
        if (mc == null || level == null || positions.isEmpty()) return CompiledMesh.EMPTY;
        var dispatcher = mc.getBlockRenderer();
        var rand = RandomSource.create();
        var tris = new ArrayList<CompiledMesh.Tri>(positions.size() * 12);
        int faceId = 0;

        for (BlockPos bp : positions) {
            var state = level.getBlockState(bp);
            if (state.isAir()) continue;
            var model = dispatcher.getBlockModel(state);
            // Iterate all render types so both vanilla JSON and NeoForge CompositeModel/OBJ (CRH1/CRH2) emit quads.
            var quads = new ArrayList<BakedQuad>();
            var renderTypes = model.getRenderTypes(state, rand, ModelData.EMPTY);
            if (!renderTypes.isEmpty()) {
                for (RenderType layer : renderTypes) {
                    if (facingHint != null) {
                        rand.setSeed(state.getSeed(bp));
                        quads.addAll(model.getQuads(state, facingHint, rand, ModelData.EMPTY, layer));
                    } else {
                        for (Direction d : Direction.values()) {
                            rand.setSeed(state.getSeed(bp));
                            quads.addAll(model.getQuads(state, d, rand, ModelData.EMPTY, layer));
                        }
                    }
                    rand.setSeed(state.getSeed(bp));
                    quads.addAll(model.getQuads(state, null, rand, ModelData.EMPTY, layer));
                }
            }
            if (quads.isEmpty()) {
                if (facingHint != null) {
                    rand.setSeed(state.getSeed(bp));
                    quads.addAll(model.getQuads(state, facingHint, rand, ModelData.EMPTY, null));
                } else {
                    for (Direction d : Direction.values()) {
                        rand.setSeed(state.getSeed(bp));
                        quads.addAll(model.getQuads(state, d, rand, ModelData.EMPTY, null));
                    }
                }
                rand.setSeed(state.getSeed(bp));
                quads.addAll(model.getQuads(state, null, rand, ModelData.EMPTY, null));
            }

            for (BakedQuad q : quads) {
                faceId = unpackQuad(q, bp.getX(), bp.getY(), bp.getZ(), "atlas:blocks", false, 0, faceId, tris);
            }
        }
        return tris.isEmpty() ? CompiledMesh.EMPTY : CompiledMesh.of(tris);
    }

    /**
     * Compiles a connected cluster of window-cut non-StaticShell blocks into a single seamless world-space
     * CompiledMesh ready for CoplanarLayerCutter:
     * - Culls internal shared full opaque faces between adjacent blocks inside {@code cutBlocks} (zero cross-block seams!).
     * - Culls hidden side faces against uncut opaque neighbors along non-cut axes (zero z-fighting with Sodium terrain!).
     * - Preserves real RenderType materials (solid/cutout/translucent) and BlockColors tinting.
     */
    static CompiledMesh compileWorldBlocksForCut(Level level, Set<BlockPos> cutBlocks, Set<Direction.Axis> cutAxes) {
        var mc = Minecraft.getInstance();
        if (mc == null || level == null || cutBlocks.isEmpty()) return CompiledMesh.EMPTY;
        var dispatcher = mc.getBlockRenderer();
        var blockColors = mc.getBlockColors();
        var rand = RandomSource.create();
        var tris = new ArrayList<CompiledMesh.Tri>(cutBlocks.size() * 12);
        int faceId = 0;

        for (BlockPos bp : cutBlocks) {
            BlockState state = level.getBlockState(bp);
            if (state.isAir()) continue;
            BakedModel model = dispatcher.getBlockModel(state);
            var renderTypes = model.getRenderTypes(state, rand, ModelData.EMPTY);
            var layers = new ArrayList<RenderType>(4);
            for (RenderType rt : renderTypes) layers.add(rt);
            if (layers.isEmpty()) layers.add(null);

            var selfOcc = state.canOcclude() ? state.getOcclusionShape(level, bp) : null;
            for (RenderType layer : layers) {
                String mat = layer != null ? materialForRenderType(layer) : "atlas:blocks";
                boolean translucent = mat.endsWith(":translucent");

                for (Direction d : Direction.values()) {
                    if (shouldCullCutBlockFace(level, bp, state, selfOcc, d, cutBlocks, cutAxes)) continue;
                    rand.setSeed(state.getSeed(bp));
                    for (BakedQuad q : model.getQuads(state, d, rand, ModelData.EMPTY, layer)) {
                        int argb = quadArgb(blockColors, state, level, bp, q);
                        faceId = unpackQuad(q, bp.getX(), bp.getY(), bp.getZ(), mat, translucent, 0, argb, faceId, tris);
                    }
                }
                rand.setSeed(state.getSeed(bp));
                for (BakedQuad q : model.getQuads(state, null, rand, ModelData.EMPTY, layer)) {
                    int argb = quadArgb(blockColors, state, level, bp, q);
                    faceId = unpackQuad(q, bp.getX(), bp.getY(), bp.getZ(), mat, translucent, 0, argb, faceId, tris);
                }
            }
        }
        if (tris.isEmpty()) return CompiledMesh.EMPTY;
        tris.sort(Comparator
                .comparing(CompiledMesh.Tri::translucent)
                .thenComparing(CompiledMesh.Tri::materialId)
                .thenComparingInt(CompiledMesh.Tri::argb));
        return CompiledMesh.of(tris);
    }

    private static boolean shouldCullCutBlockFace(Level level, BlockPos bp, BlockState state,
                                                  net.minecraft.world.phys.shapes.VoxelShape selfOcc,
                                                  Direction d, Set<BlockPos> cutBlocks, Set<Direction.Axis> cutAxes) {
        if (selfOcc == null || !Block.isFaceFull(selfOcc, d)) return false;
        BlockPos np = bp.relative(d);
        if (!level.hasChunk(np.getX() >> 4, np.getZ() >> 4)) return false;
        BlockState nState = level.getBlockState(np);
        if (!nState.canOcclude()) return false;
        if (!Block.isFaceFull(nState.getOcclusionShape(level, np), d.getOpposite())) return false;
        // Both blocks are inside the cut cluster: shared internal face is completely invisible -> cull it!
        if (cutBlocks.contains(np)) return true;
        // Against an uncut solid neighbor: cull side faces perpendicular to the cut axes (Sodium exposes the
        // uncut neighbor's face cleanly via shouldExposeFaceIntoCut), while keeping entry/exit faces along cutAxes
        // so CoplanarLayerCutter always sees the exact front & back thickness of the cut wall!
        return !cutAxes.contains(d.getAxis());
    }

    /**
     * Compiles contraption-local host blocks (including StaticShell cached meshes) into a single local-space
     * CompiledMesh for decal projection on moving trains/contraptions.
     */
    static CompiledMesh compileContraptionBlocks(Map<BlockPos, StructureBlockInfo> blocks, Set<BlockPos> sampleBlocks) {
        var mc = Minecraft.getInstance();
        if (mc == null || blocks == null || sampleBlocks == null || sampleBlocks.isEmpty()) return CompiledMesh.EMPTY;
        var dispatcher = mc.getBlockRenderer();
        var rand = RandomSource.create();
        var tris = new ArrayList<CompiledMesh.Tri>(sampleBlocks.size() * 12);
        int faceId = 0;

        for (BlockPos bp : sampleBlocks) {
            StructureBlockInfo info = blocks.get(bp);
            if (info == null || info.state() == null || info.state().isAir()) continue;
            BlockState state = info.state();
            CompiledMesh shell = HexieRendererAdapter.compiledShellModel(state);
            if (shell != null && !shell.isEmpty()) {
                double ox = bp.getX(), oy = bp.getY(), oz = bp.getZ();
                for (var t : shell.triangles()) {
                    var a = new CompiledMesh.Vertex(new Vec(ox + t.a().pos().x(), oy + t.a().pos().y(), oz + t.a().pos().z()),
                            t.a().normal(), t.a().u(), t.a().v());
                    var b = new CompiledMesh.Vertex(new Vec(ox + t.b().pos().x(), oy + t.b().pos().y(), oz + t.b().pos().z()),
                            t.b().normal(), t.b().u(), t.b().v());
                    var c = new CompiledMesh.Vertex(new Vec(ox + t.c().pos().x(), oy + t.c().pos().y(), oz + t.c().pos().z()),
                            t.c().normal(), t.c().u(), t.c().v());
                    tris.add(new CompiledMesh.Tri(faceId++, a, b, c, t.materialId(), t.argb(), t.translucent()));
                }
                continue;
            }
            var model = dispatcher.getBlockModel(state);
            var quads = new ArrayList<BakedQuad>();
            var renderTypes = model.getRenderTypes(state, rand, ModelData.EMPTY);
            if (!renderTypes.isEmpty()) {
                for (RenderType layer : renderTypes) {
                    for (Direction d : Direction.values()) {
                        rand.setSeed(state.getSeed(bp));
                        quads.addAll(model.getQuads(state, d, rand, ModelData.EMPTY, layer));
                    }
                    rand.setSeed(state.getSeed(bp));
                    quads.addAll(model.getQuads(state, null, rand, ModelData.EMPTY, layer));
                }
            }
            if (quads.isEmpty()) {
                for (Direction d : Direction.values()) {
                    rand.setSeed(state.getSeed(bp));
                    quads.addAll(model.getQuads(state, d, rand, ModelData.EMPTY, null));
                }
                rand.setSeed(state.getSeed(bp));
                quads.addAll(model.getQuads(state, null, rand, ModelData.EMPTY, null));
            }
            for (BakedQuad q : quads) {
                faceId = unpackQuad(q, bp.getX(), bp.getY(), bp.getZ(), "atlas:blocks", false, 0, faceId, tris);
            }
        }
        return tris.isEmpty() ? CompiledMesh.EMPTY : CompiledMesh.of(tris);
    }

    /**
     * Compiles a contraption's window-cut non-StaticShell blocks in contraption-local coordinates,
     * culling shared internal faces between adjacent cut blocks and hidden side faces against uncut solid neighbors.
     */
    static CompiledMesh compileContraptionBlocksForCut(Map<BlockPos, StructureBlockInfo> blocks,
                                                       Set<BlockPos> cutBlocks,
                                                       Set<Direction.Axis> cutAxes) {
        var mc = Minecraft.getInstance();
        if (mc == null || blocks == null || cutBlocks == null || cutBlocks.isEmpty()) return CompiledMesh.EMPTY;
        var dispatcher = mc.getBlockRenderer();
        var blockColors = mc.getBlockColors();
        var rand = RandomSource.create();
        var tris = new ArrayList<CompiledMesh.Tri>(cutBlocks.size() * 12);
        int faceId = 0;

        for (BlockPos bp : cutBlocks) {
            StructureBlockInfo info = blocks.get(bp);
            if (info == null || info.state() == null || info.state().isAir()) continue;
            BlockState state = info.state();
            BakedModel model = dispatcher.getBlockModel(state);
            var renderTypes = model.getRenderTypes(state, rand, ModelData.EMPTY);
            var layers = new ArrayList<RenderType>(4);
            for (RenderType rt : renderTypes) layers.add(rt);
            if (layers.isEmpty()) layers.add(null);

            var selfOcc = state.canOcclude() ? state.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) : null;
            for (RenderType layer : layers) {
                String mat = layer != null ? materialForRenderType(layer) : "atlas:blocks";
                boolean translucent = mat.endsWith(":translucent");

                for (Direction d : Direction.values()) {
                    if (shouldCullContraptionCutFace(blocks, bp, selfOcc, d, cutBlocks, cutAxes)) continue;
                    rand.setSeed(state.getSeed(bp));
                    for (BakedQuad q : model.getQuads(state, d, rand, ModelData.EMPTY, layer)) {
                        int argb = quadArgb(blockColors, state, null, bp, q);
                        faceId = unpackQuad(q, bp.getX(), bp.getY(), bp.getZ(), mat, translucent, 0, argb, faceId, tris);
                    }
                }
                rand.setSeed(state.getSeed(bp));
                for (BakedQuad q : model.getQuads(state, null, rand, ModelData.EMPTY, layer)) {
                    int argb = quadArgb(blockColors, state, null, bp, q);
                    faceId = unpackQuad(q, bp.getX(), bp.getY(), bp.getZ(), mat, translucent, 0, argb, faceId, tris);
                }
            }
        }
        if (tris.isEmpty()) return CompiledMesh.EMPTY;
        tris.sort(Comparator
                .comparing(CompiledMesh.Tri::translucent)
                .thenComparing(CompiledMesh.Tri::materialId)
                .thenComparingInt(CompiledMesh.Tri::argb));
        return CompiledMesh.of(tris);
    }

    private static boolean shouldCullContraptionCutFace(Map<BlockPos, StructureBlockInfo> blocks,
                                                        BlockPos bp,
                                                        net.minecraft.world.phys.shapes.VoxelShape selfOcc,
                                                        Direction d,
                                                        Set<BlockPos> cutBlocks,
                                                        Set<Direction.Axis> cutAxes) {
        if (selfOcc == null || !Block.isFaceFull(selfOcc, d)) return false;
        BlockPos np = bp.relative(d);
        StructureBlockInfo nInfo = blocks.get(np);
        if (nInfo == null || nInfo.state() == null || !nInfo.state().canOcclude()) return false;
        if (!Block.isFaceFull(nInfo.state().getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), d.getOpposite())) {
            return false;
        }
        if (cutBlocks.contains(np)) return true;
        return !cutAxes.contains(d.getAxis());
    }

    private static int quadArgb(net.minecraft.client.color.block.BlockColors colors, BlockState state,
                                Level level, BlockPos pos, BakedQuad q) {
        if (!q.isTinted() || colors == null) return 0xFFFFFFFF;
        int rgb = colors.getColor(state, level, pos, q.getTintIndex());
        return rgb == -1 ? 0xFFFFFFFF : (0xFF000000 | (rgb & 0x00FFFFFF));
    }

    private static String materialForRenderType(RenderType layer) {
        if (layer == RenderType.translucent() || layer == RenderType.tripwire()) return "atlas:blocks:translucent";
        if (layer == RenderType.cutout() || layer == RenderType.cutoutMipped()) return "atlas:blocks:cutout";
        return "atlas:blocks";
    }

    private static int unpackQuad(BakedQuad q, double ox, double oy, double oz,
                                  String materialId, boolean translucent, int alphaThreshold,
                                  int nextFaceId, List<CompiledMesh.Tri> out) {
        return unpackQuad(q, ox, oy, oz, materialId, translucent, alphaThreshold, 0xFFFFFFFF, nextFaceId, out);
    }

    private static int unpackQuad(BakedQuad q, double ox, double oy, double oz,
                                  String materialId, boolean translucent, int alphaThreshold, int argb,
                                  int nextFaceId, List<CompiledMesh.Tri> out) {
        int[] vd = q.getVertices();
        if (vd.length < 32) return nextFaceId;
        var vs = new CompiledMesh.Vertex[4];
        Vec[] ps = new Vec[4];
        float[] us = new float[4], vts = new float[4];
        Vec[] ns = new Vec[4];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            ps[i] = new Vec(
                    ox + Float.intBitsToFloat(vd[o]),
                    oy + Float.intBitsToFloat(vd[o + 1]),
                    oz + Float.intBitsToFloat(vd[o + 2]));
            us[i] = Float.intBitsToFloat(vd[o + 4]);
            vts[i] = Float.intBitsToFloat(vd[o + 5]);
            int np = vd[o + 7];
            double nx = ((byte) (np & 0xFF)) / 127.0;
            double ny = ((byte) ((np >> 8) & 0xFF)) / 127.0;
            double nz = ((byte) ((np >> 16) & 0xFF)) / 127.0;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            ns[i] = nl > 1e-4 ? new Vec(nx / nl, ny / nl, nz / nl) : null;
        }
        Vec fn = ps[1].minus(ps[0]).cross(ps[2].minus(ps[0]));
        double fl = Math.sqrt(fn.dot(fn));
        if (fl <= 1e-10) return nextFaceId;
        Vec fallbackN = fn.times(1.0 / fl);
        for (int i = 0; i < 4; i++) {
            vs[i] = new CompiledMesh.Vertex(ps[i], ns[i] != null ? ns[i] : fallbackN, us[i], vts[i]);
        }

        // For cutout carriage shells and framed translucent window models, prune transparent glass quads and
        // greedy-merge mixed quads into opaque sub-quads so raytraced light streams cleanly through every window!
        if (alphaThreshold > 0 && q.getSprite() != null) {
            int pruned = tryEmitAlphaPrunedCutout(q.getSprite(), vs, materialId, alphaThreshold, nextFaceId, out);
            if (pruned >= 0) return pruned;
        }

        CompiledMesh.Tri t0 = new CompiledMesh.Tri(nextFaceId, vs[0], vs[1], vs[2], materialId, argb, translucent);
        if (t0.area2() > 1e-10) out.add(t0);
        CompiledMesh.Tri t1 = new CompiledMesh.Tri(nextFaceId, vs[0], vs[2], vs[3], materialId, argb, translucent);
        if (t1.area2() > 1e-10) out.add(t1);
        return nextFaceId + 1;
    }

    /**
     * Evaluates the quad's sprite alpha mask. Returns updated nextFaceId if handled (0 tris for fully transparent
     * quads, greedy-merged opaque sub-quads for mixed window-hole quads), or -1 to emit the original quad unchanged.
     */
    private static int tryEmitAlphaPrunedCutout(TextureAtlasSprite sprite, CompiledMesh.Vertex[] vs,
                                                String materialId, int alphaThreshold, int nextFaceId,
                                                List<CompiledMesh.Tri> out) {
        SpriteContents contents = sprite.contents();
        if (contents == null) return -1;
        int sw = contents.width(), sh = contents.height();
        if (sw <= 0 || sh <= 0) return -1;
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();
        float du = u1 - u0, dv = v1 - v0;
        if (Math.abs(du) < 1e-9f || Math.abs(dv) < 1e-9f) return -1;

        double[] su = new double[4], sv = new double[4];
        double minSu = Double.POSITIVE_INFINITY, maxSu = Double.NEGATIVE_INFINITY;
        double minSv = Double.POSITIVE_INFINITY, maxSv = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            su[i] = (vs[i].u() - u0) / du;
            sv[i] = (vs[i].v() - v0) / dv;
            if (su[i] < minSu) minSu = su[i];
            if (su[i] > maxSu) maxSu = su[i];
            if (sv[i] < minSv) minSv = sv[i];
            if (sv[i] > maxSv) maxSv = sv[i];
        }
        int gxCount = Math.max(1, Math.min(32, (int) Math.ceil((maxSu - minSu) * sw)));
        int gyCount = Math.max(1, Math.min(32, (int) Math.ceil((maxSv - minSv) * sh)));
        NativeImage img = readableImage(contents);

        boolean[] opaque = new boolean[gxCount * gyCount];
        int opaqueCount = 0;
        for (int gy = 0; gy < gyCount; gy++) {
            double t = (gy + 0.5) / gyCount;
            for (int gx = 0; gx < gxCount; gx++) {
                double s = (gx + 0.5) / gxCount;
                double lu = bilerp(su[0], su[1], su[2], su[3], s, t);
                double lv = bilerp(sv[0], sv[1], sv[2], sv[3], s, t);
                int px = Math.max(0, Math.min(sw - 1, (int) Math.floor(lu * sw)));
                int py = Math.max(0, Math.min(sh - 1, (int) Math.floor(lv * sh)));
                boolean isOpaque = sampleOpaque(contents, img, px, py, alphaThreshold);
                if (isOpaque) {
                    opaque[gy * gxCount + gx] = true;
                    opaqueCount++;
                }
            }
        }
        if (opaqueCount == 0) {
            return nextFaceId; // 100% transparent cutout quad (e.g. window glass faces in *_body OBJ): drop completely!
        }
        if (opaqueCount == opaque.length) {
            return -1; // 100% opaque: emit original 2 triangles
        }
        // Mixed quad (opaque frame/wall + transparent window opening): 2D greedy rectangle merge!
        int fid = nextFaceId;
        for (int y = 0; y < gyCount; y++) {
            for (int x = 0; x < gxCount; x++) {
                if (!opaque[y * gxCount + x]) continue;
                int rw = 1;
                while (x + rw < gxCount && opaque[y * gxCount + x + rw]) rw++;
                int rh = 1;
                outerH:
                while (y + rh < gyCount) {
                    int row = (y + rh) * gxCount + x;
                    for (int dx = 0; dx < rw; dx++) {
                        if (!opaque[row + dx]) break outerH;
                    }
                    rh++;
                }
                for (int dy = 0; dy < rh; dy++) {
                    int row = (y + dy) * gxCount + x;
                    for (int dx = 0; dx < rw; dx++) opaque[row + dx] = false;
                }
                double s0 = (double) x / gxCount, s1 = (double) (x + rw) / gxCount;
                double t0 = (double) y / gyCount, t1 = (double) (y + rh) / gyCount;
                var c0 = bilerpVertex(vs, s0, t0);
                var c1 = bilerpVertex(vs, s0, t1);
                var c2 = bilerpVertex(vs, s1, t1);
                var c3 = bilerpVertex(vs, s1, t0);
                var tri0 = new CompiledMesh.Tri(fid, c0, c1, c2, materialId, 0xFFFFFFFF, false);
                if (tri0.area2() > 1e-10) out.add(tri0);
                var tri1 = new CompiledMesh.Tri(fid, c0, c2, c3, materialId, 0xFFFFFFFF, false);
                if (tri1.area2() > 1e-10) out.add(tri1);
                fid++;
            }
        }
        return fid;
    }

    private static NativeImage readableImage(SpriteContents contents) {
        try {
            NativeImage orig = contents.getOriginalImage();
            if (orig != null && orig.getWidth() > 0 && orig.getHeight() > 0) {
                orig.getPixelRGBA(0, 0); // verifies native pointer is still valid
                return orig;
            }
        } catch (Throwable ignored) {}
        try {
            if (contents.byMipLevel != null && contents.byMipLevel.length > 0 && contents.byMipLevel[0] != null) {
                contents.byMipLevel[0].getPixelRGBA(0, 0);
                return contents.byMipLevel[0];
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean sampleOpaque(SpriteContents contents, NativeImage img, int px, int py, int alphaThreshold) {
        if (img != null) {
            try {
                int alpha = (img.getPixelRGBA(px, py) >>> 24) & 0xFF;
                return alpha >= alphaThreshold;
            } catch (Throwable ignored) {}
        }
        try {
            return !contents.isTransparent(0, px, py);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static double bilerp(double v0, double v1, double v2, double v3, double s, double t) {
        double a = v0 + (v1 - v0) * t;
        double b = v3 + (v2 - v3) * t;
        return a + (b - a) * s;
    }

    private static CompiledMesh.Vertex bilerpVertex(CompiledMesh.Vertex[] vs, double s, double t) {
        double px = bilerp(vs[0].pos().x(), vs[1].pos().x(), vs[2].pos().x(), vs[3].pos().x(), s, t);
        double py = bilerp(vs[0].pos().y(), vs[1].pos().y(), vs[2].pos().y(), vs[3].pos().y(), s, t);
        double pz = bilerp(vs[0].pos().z(), vs[1].pos().z(), vs[2].pos().z(), vs[3].pos().z(), s, t);
        double nx = bilerp(vs[0].normal().x(), vs[1].normal().x(), vs[2].normal().x(), vs[3].normal().x(), s, t);
        double ny = bilerp(vs[0].normal().y(), vs[1].normal().y(), vs[2].normal().y(), vs[3].normal().y(), s, t);
        double nz = bilerp(vs[0].normal().z(), vs[1].normal().z(), vs[2].normal().z(), vs[3].normal().z(), s, t);
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        Vec n = nl > 1e-6 ? new Vec(nx / nl, ny / nl, nz / nl) : vs[0].normal();
        double u = bilerp(vs[0].u(), vs[1].u(), vs[2].u(), vs[3].u(), s, t);
        double v = bilerp(vs[0].v(), vs[1].v(), vs[2].v(), vs[3].v(), s, t);
        return new CompiledMesh.Vertex(new Vec(px, py, pz), n, u, v);
    }
}
