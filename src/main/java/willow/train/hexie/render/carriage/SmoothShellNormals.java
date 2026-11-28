package willow.train.hexie.render.carriage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Computes crease-angle-aware smooth per-vertex normals ONCE at model bake time for curved OBJ carriage shells
 * (e.g. CRH1/CRH2 noses & streamlined roofs that export flat per-face normals).
 * Feeds both StandardGeometry (static world shells) and Flywheel (assembled Create trains) so curved noses
 * shade smoothly per-pixel instead of flat-faceted polygons, while preserving crisp >=60-degree architectural edges.
 */
final class SmoothShellNormals {
    private static final float CREASE_COS = 0.50f; // 60 degrees: smooths nose/roof curves, preserves crisp 90-degree edges

    private record PosKey(int x, int y, int z) {
        static PosKey of(float x, float y, float z) {
            return new PosKey(Math.round(x * 2048f), Math.round(y * 2048f), Math.round(z * 2048f));
        }
    }

    private record BucketKey(RenderType layer, Direction side) {}

    private record FaceInfo(float nx, float ny, float nz, float area) {}

    private SmoothShellNormals() {}

    static BakedModel smooth(BakedModel original, BlockState state) {
        if (original == null || state == null) return original;
        var rand = RandomSource.create(42L);
        var renderTypes = original.getRenderTypes(state, rand, ModelData.EMPTY);
        var layers = new ArrayList<RenderType>(4);
        for (RenderType rt : renderTypes) layers.add(rt);
        if (layers.isEmpty()) layers.add(null);

        var rawBuckets = new HashMap<BucketKey, List<BakedQuad>>();
        var posFaces = new HashMap<PosKey, List<FaceInfo>>(2048);
        int totalQuads = 0;

        for (RenderType layer : layers) {
            for (int s = -1; s < 6; s++) {
                Direction side = s < 0 ? null : Direction.values()[s];
                rand.setSeed(42L);
                List<BakedQuad> quads = original.getQuads(state, side, rand, ModelData.EMPTY, layer);
                if (quads.isEmpty()) continue;
                rawBuckets.put(new BucketKey(layer, side), quads);
                totalQuads += quads.size();
                for (BakedQuad q : quads) {
                    int[] vd = q.getVertices();
                    if (vd.length < 32) continue;
                    FaceInfo fi = computeFace(vd);
                    if (fi == null) continue;
                    for (int i = 0; i < 4; i++) {
                        int o = i * 8;
                        PosKey pk = PosKey.of(
                                Float.intBitsToFloat(vd[o]),
                                Float.intBitsToFloat(vd[o + 1]),
                                Float.intBitsToFloat(vd[o + 2]));
                        posFaces.computeIfAbsent(pk, k -> new ArrayList<>(4)).add(fi);
                    }
                }
            }
        }
        if (totalQuads == 0) return original;

        var smoothedBuckets = new HashMap<BucketKey, List<BakedQuad>>(rawBuckets.size());
        for (var e : rawBuckets.entrySet()) {
            var srcList = e.getValue();
            var dstList = new ArrayList<BakedQuad>(srcList.size());
            for (BakedQuad q : srcList) {
                int[] vd = q.getVertices();
                if (vd.length < 32) {
                    dstList.add(q);
                    continue;
                }
                FaceInfo fi = computeFace(vd);
                if (fi == null) {
                    dstList.add(q);
                    continue;
                }
                int[] copy = vd.clone();
                for (int i = 0; i < 4; i++) {
                    int o = i * 8;
                    PosKey pk = PosKey.of(
                            Float.intBitsToFloat(vd[o]),
                            Float.intBitsToFloat(vd[o + 1]),
                            Float.intBitsToFloat(vd[o + 2]));
                    List<FaceInfo> neighbors = posFaces.get(pk);
                    float sx = fi.nx * fi.area;
                    float sy = fi.ny * fi.area;
                    float sz = fi.nz * fi.area;
                    if (neighbors != null) {
                        for (FaceInfo nf : neighbors) {
                            if (nf == fi) continue;
                            float dot = fi.nx * nf.nx + fi.ny * nf.ny + fi.nz * nf.nz;
                            if (dot >= CREASE_COS) {
                                sx += nf.nx * nf.area;
                                sy += nf.ny * nf.area;
                                sz += nf.nz * nf.area;
                            }
                        }
                    }
                    float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                    float nx = len > 1e-6f ? sx / len : fi.nx;
                    float ny = len > 1e-6f ? sy / len : fi.ny;
                    float nz = len > 1e-6f ? sz / len : fi.nz;
                    copy[o + 7] = packNormal(nx, ny, nz);
                }
                dstList.add(new BakedQuad(copy, q.getTintIndex(), q.getDirection(), q.getSprite(),
                        q.isShade(), q.hasAmbientOcclusion()));
            }
            smoothedBuckets.put(e.getKey(), List.copyOf(dstList));
        }

        // Also cache null-layer fallback if original was queried with null RenderType
        return new SmoothedModel(original, smoothedBuckets);
    }

    private static FaceInfo computeFace(int[] vd) {
        float x0 = Float.intBitsToFloat(vd[0]), y0 = Float.intBitsToFloat(vd[1]), z0 = Float.intBitsToFloat(vd[2]);
        float x1 = Float.intBitsToFloat(vd[8]), y1 = Float.intBitsToFloat(vd[9]), z1 = Float.intBitsToFloat(vd[10]);
        float x2 = Float.intBitsToFloat(vd[16]), y2 = Float.intBitsToFloat(vd[17]), z2 = Float.intBitsToFloat(vd[18]);
        float x3 = Float.intBitsToFloat(vd[24]), y3 = Float.intBitsToFloat(vd[25]), z3 = Float.intBitsToFloat(vd[26]);
        // Cross of diagonals (v2 - v0) x (v3 - v1) handles degenerate triangles in quads cleanly!
        float ax = x2 - x0, ay = y2 - y0, az = z2 - z0;
        float bx = x3 - x1, by = y3 - y1, bz = z3 - z1;
        float cx = ay * bz - az * by;
        float cy = az * bx - ax * bz;
        float cz = ax * by - ay * bx;
        float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len <= 1e-7f) return null;
        return new FaceInfo(cx / len, cy / len, cz / len, len * 0.5f);
    }

    private static int packNormal(float nx, float ny, float nz) {
        int bx = Math.max(-127, Math.min(127, Math.round(nx * 127f))) & 0xFF;
        int by = Math.max(-127, Math.min(127, Math.round(ny * 127f))) & 0xFF;
        int bz = Math.max(-127, Math.min(127, Math.round(nz * 127f))) & 0xFF;
        return bx | (by << 8) | (bz << 16);
    }

    private static final class SmoothedModel extends BakedModelWrapper<BakedModel> {
        private final Map<BucketKey, List<BakedQuad>> buckets;

        SmoothedModel(BakedModel original, Map<BucketKey, List<BakedQuad>> buckets) {
            super(original);
            this.buckets = buckets;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                                        ModelData data, RenderType layer) {
            List<BakedQuad> cached = buckets.get(new BucketKey(layer, side));
            if (cached != null) return cached;
            if (layer == null) {
                var all = new ArrayList<BakedQuad>();
                for (var e : buckets.entrySet()) {
                    if (e.getKey().side == side) all.addAll(e.getValue());
                }
                if (!all.isEmpty()) return all;
            }
            return super.getQuads(state, side, random, data, layer);
        }
    }
}
