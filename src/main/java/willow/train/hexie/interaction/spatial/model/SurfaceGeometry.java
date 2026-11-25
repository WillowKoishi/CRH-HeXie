package willow.train.hexie.interaction.spatial.model;

import java.util.*;

/** Display and marking use the visible triangles, cutting uses the optional declared proxy or the original shell. */
public record SurfaceGeometry(SurfaceMesh visible, Optional<SurfaceMesh> cutShell, long topologyRevision) {
    public SurfaceGeometry(SurfaceMesh visible, Optional<SurfaceMesh> cutShell) {
        this(visible, cutShell, 0);
    }
    public SurfaceGeometry(SurfaceMesh visible) {
        this(visible, Optional.empty());
    }
    /** Representative outer planar face for the original single-polygon SPI. Folded consumers use visible(). */
    public Optional<FaceGeometry> face(Face side) {
        var axis = inward(side).scale(-1);
        SurfaceMesh.Triangle outer = null;
        double best = Double.NEGATIVE_INFINITY;
        for (var t : visible.triangles()) {
            var n = t.normal();
            if (n.cross(axis).length() > 1e-9 * n.length() || dot(n, axis) <= 0) continue;
            double depth = dot(t.a(), axis);
            if (depth > best) {
                best = depth;
                outer = t;
            }
        }
        if (outer == null) return Optional.empty();
        for (var t : visible.triangles())
            if (outer.a().equals(t.a()) && outer.c().equals(t.b()) && dot(t.normal(), axis) > 0
                    && Math.abs(dot(t.c(), axis) - best) < 1e-9)
                return Optional.of(new FaceGeometry(List.of(outer.a(), outer.b(), outer.c(), t.c())));
        return Optional.of(new FaceGeometry(List.of(outer.a(), outer.b(), outer.c())));
    }
    public static Vec inward(Face side) {
        return switch (side) {
            case NORTH -> new Vec(0, 0, 1);
            case SOUTH -> new Vec(0, 0, -1);
            case WEST -> new Vec(1, 0, 0);
            case EAST -> new Vec(-1, 0, 0);
            case DOWN -> new Vec(0, 1, 0);
            case UP -> new Vec(0, -1, 0);
        };
    }
    private static double dot(Vec a, Vec b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }
    public SurfaceMesh cuttingMesh() {
        return cutShell.orElse(visible);
    }
}
