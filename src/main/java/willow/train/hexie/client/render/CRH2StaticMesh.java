package willow.train.hexie.client.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.client.renderer.RenderType;

/** Resource-time clipping only: retain the authored surface, UVs and normals. */
final class CRH2StaticMesh {
    record Face(BlockPos cell, BakedQuad quad) {}
    final List<Face> faces;
    final int reach;

    CRH2StaticMesh(BakedModel model, BlockState state) {
        var result = new ArrayList<Face>();
        var random = RandomSource.create(42);
        for (int side = -1; side < 6; side++) {
            random.setSeed(42);
            for (var quad : model.getQuads(state, side < 0 ? null : Direction.values()[side], random,
                    ModelData.EMPTY, RenderType.cutout())) {
                var vertices = new Vertex[4];
                for (int i = 0; i < 4; i++) vertices[i] = Vertex.read(quad.getVertices(), i);
                split(List.of(vertices[0], vertices[1], vertices[2]), quad, result);
                split(List.of(vertices[0], vertices[2], vertices[3]), quad, result);
            }
        }
        faces = List.copyOf(result);
        reach = faces.stream().mapToInt(f -> Math.max(Math.abs(f.cell.getX()),
                Math.max(Math.abs(f.cell.getY()), Math.abs(f.cell.getZ())))).max().orElse(0) + 1;
    }

    private static void split(List<Vertex> triangle, BakedQuad source, List<Face> output) {
        if (areaSquared(triangle.get(0), triangle.get(1), triangle.get(2)) < 1e-18) return;
        int[] min = new int[3], max = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (var v : triangle) { lo = Math.min(lo, v.a[axis]); hi = Math.max(hi, v.a[axis]); }
            min[axis] = (int) Math.floor(lo);
            // A face exactly on a grid plane belongs to one cell, never both.
            max[axis] = Math.max(min[axis], (int) Math.ceil(hi) - 1);
        }
        for (int x = min[0]; x <= max[0]; x++)
            for (int y = min[1]; y <= max[1]; y++)
                for (int z = min[2]; z <= max[2]; z++) {
                    var cell = new BlockPos(x, y, z);
                    List<Vertex> polygon = triangle;
                    int[] origin = {x, y, z};
                    for (int axis = 0; axis < 3 && !polygon.isEmpty(); axis++) {
                        polygon = clip(polygon, axis, origin[axis], true);
                        polygon = clip(polygon, axis, origin[axis] + 1, false);
                    }
                    for (int i = 1; i + 1 < polygon.size(); i++) {
                        var a = polygon.get(0); var b = polygon.get(i); var c = polygon.get(i + 1);
                        if (areaSquared(a, b, c) < 1e-18) continue;
                        int[] packed = new int[32];
                        a.write(packed, 0, cell); b.write(packed, 1, cell);
                        c.write(packed, 2, cell); c.write(packed, 3, cell);
                        output.add(new Face(cell, new BakedQuad(packed, source.getTintIndex(),
                                source.getDirection(), source.getSprite(), false, false)));
                    }
                }
    }

    private static List<Vertex> clip(List<Vertex> input, int axis, double plane, boolean above) {
        if (input.isEmpty()) return input;
        var output = new ArrayList<Vertex>();
        var previous = input.getLast();
        boolean previousInside = above ? previous.a[axis] >= plane : previous.a[axis] <= plane;
        for (var current : input) {
            boolean inside = above ? current.a[axis] >= plane : current.a[axis] <= plane;
            if (inside != previousInside) {
                double t = (plane - previous.a[axis]) / (current.a[axis] - previous.a[axis]);
                var intersection = previous.lerp(current, t);
                intersection.a[axis] = plane;
                output.add(intersection);
            }
            if (inside) output.add(current);
            previous = current; previousInside = inside;
        }
        return output;
    }

    private static double areaSquared(Vertex a, Vertex b, Vertex c) {
        double x = b.a[0]-a.a[0], y = b.a[1]-a.a[1], z = b.a[2]-a.a[2];
        double u = c.a[0]-a.a[0], v = c.a[1]-a.a[1], w = c.a[2]-a.a[2];
        return Math.pow(y*w-z*v,2) + Math.pow(z*u-x*w,2) + Math.pow(x*v-y*u,2);
    }

    private static final class Vertex {
        // Position, UV, RGBA, normal. Interpolation does not smooth adjacent faces.
        final double[] a = new double[12];
        static Vertex read(int[] data, int vertex) {
            var v = new Vertex(); int p = vertex * (data.length / 4);
            for (int i = 0; i < 3; i++) v.a[i] = Float.intBitsToFloat(data[p+i]);
            v.a[3] = Float.intBitsToFloat(data[p+4]); v.a[4] = Float.intBitsToFloat(data[p+5]);
            for (int i = 0; i < 4; i++) v.a[5+i] = (data[p+3] >>> (8*i)) & 255;
            for (int i = 0; i < 3; i++) v.a[9+i] = (byte) (data[p+7] >>> (8*i));
            return v;
        }
        Vertex lerp(Vertex other, double t) {
            var v = new Vertex();
            for (int i = 0; i < a.length; i++) v.a[i] = a[i] + (other.a[i]-a[i])*t;
            return v;
        }
        void write(int[] data, int vertex, BlockPos cell) {
            int p = vertex * 8;
            data[p] = Float.floatToRawIntBits((float)(a[0]-cell.getX()));
            data[p+1] = Float.floatToRawIntBits((float)(a[1]-cell.getY()));
            data[p+2] = Float.floatToRawIntBits((float)(a[2]-cell.getZ()));
            for (int i = 0; i < 4; i++) data[p+3] |= ((int)Math.round(a[5+i]) & 255) << (8*i);
            data[p+4] = Float.floatToRawIntBits((float)a[3]);
            data[p+5] = Float.floatToRawIntBits((float)a[4]);
            for (int i = 0; i < 3; i++) data[p+7] |= ((int)Math.round(a[9+i]) & 255) << (8*i);
        }
    }
}
