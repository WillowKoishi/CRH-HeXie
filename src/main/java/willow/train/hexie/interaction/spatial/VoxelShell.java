package willow.train.hexie.interaction.spatial;

import java.util.*;
import willow.train.hexie.interaction.spatial.model.*;
import willow.train.hexie.interaction.spatial.model.SurfaceMesh.Triangle;

/** Boundary of the union, with all neighboring voxel faces split on the same coordinate grid. */
public final class VoxelShell {
    public static SurfaceMesh of(List<AABB> boxes) {
        if (boxes.size() == 1) return SurfaceMesh.box(boxes.getFirst());
        var xs = new TreeSet<Double>();
        var ys = new TreeSet<Double>();
        var zs = new TreeSet<Double>();
        for (var b : boxes) {
            xs.add(b.min().x());
            xs.add(b.max().x());
            ys.add(b.min().y());
            ys.add(b.max().y());
            zs.add(b.min().z());
            zs.add(b.max().z());
        }
        var x = List.copyOf(xs);
        var y = List.copyOf(ys);
        var z = List.copyOf(zs);
        var filled = new LinkedHashSet<Cell>();
        for (int i = 1; i < x.size(); i++)
            for (int j = 1; j < y.size(); j++)
                for (int k = 1; k < z.size(); k++) {
                    double px = (x.get(i - 1) + x.get(i)) / 2, py = (y.get(j - 1) + y.get(j)) / 2,
                           pz = (z.get(k - 1) + z.get(k)) / 2;
                    if (boxes.stream().anyMatch(b
                                -> px > b.min().x() && px < b.max().x() && py > b.min().y() && py < b.max().y()
                                        && pz > b.min().z() && pz < b.max().z()))
                        filled.add(new Cell(i - 1, j - 1, k - 1));
                }
        var result = new ArrayList<Triangle>();
        for (var cell : filled) {
            int i = cell.x(), j = cell.y(), k = cell.z();
            var box =
                    new AABB(new Vec(x.get(i), y.get(j), z.get(k)), new Vec(x.get(i + 1), y.get(j + 1), z.get(k + 1)));
            for (var face : Face.values()) {
                var neighbor = switch (face) {
                    case DOWN -> new Cell(i, j - 1, k);
                    case UP -> new Cell(i, j + 1, k);
                    case NORTH -> new Cell(i, j, k - 1);
                    case SOUTH -> new Cell(i, j, k + 1);
                    case WEST -> new Cell(i - 1, j, k);
                    case EAST -> new Cell(i + 1, j, k);
                };
                if (filled.contains(neighbor)) continue;
                var v = SurfaceMesh.boxFace(box, face).vertices();
                result.add(new Triangle(v.get(0), v.get(1), v.get(2)));
                result.add(new Triangle(v.get(0), v.get(2), v.get(3)));
            }
        }
        return new SurfaceMesh(result);
    }
    private VoxelShell() {}
}
