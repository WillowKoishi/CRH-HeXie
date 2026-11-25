package willow.train.hexie.interaction.spatial.model;

import java.util.*;

/** Original, outward-wound triangles in cell coordinates. The list index is the stable pre-cut face ID. */
public record SurfaceMesh(List<Triangle> triangles) {
    public SurfaceMesh {
        triangles = List.copyOf(triangles);
    }

    public record Triangle(Vec a, Vec b, Vec c) {
        public Vec normal() {
            return b.subtract(a).cross(c.subtract(a));
        }
        public Vec at(double bWeight, double cWeight) {
            return a.add(b.subtract(a).scale(bWeight)).add(c.subtract(a).scale(cWeight));
        }
    }

    public static SurfaceMesh box(AABB box) {
        var result = new ArrayList<Triangle>();
        for (Face face : Face.values()) {
            var vertices = boxFace(box, face).vertices();
            result.add(new Triangle(vertices.get(0), vertices.get(1), vertices.get(2)));
            result.add(new Triangle(vertices.get(0), vertices.get(2), vertices.get(3)));
        }
        return new SurfaceMesh(result);
    }

    public static FaceGeometry boxFace(AABB box, Face face) {
        Vec a = box.min(), b = box.max();
        var corners =
                List.of(a, new Vec(b.x(), a.y(), a.z()), new Vec(b.x(), b.y(), a.z()), new Vec(a.x(), b.y(), a.z()),
                        new Vec(a.x(), a.y(), b.z()), new Vec(b.x(), a.y(), b.z()), b, new Vec(a.x(), b.y(), b.z()));
        var indices = switch (face) {
            case DOWN -> List.of(0, 1, 5, 4);
            case UP -> List.of(3, 7, 6, 2);
            case NORTH -> List.of(0, 3, 2, 1);
            case SOUTH -> List.of(4, 5, 6, 7);
            case WEST -> List.of(0, 4, 7, 3);
            case EAST -> List.of(1, 2, 6, 5);
        };
        return new FaceGeometry(indices.stream().map(corners::get).toList());
    }
}
