package willow.train.hexie.interaction.spatial.model;

import java.util.*;
import willow.train.hexie.interaction.spatial.model.SurfaceMesh.Triangle;

@FunctionalInterface
public interface SurfaceSource {
    Optional<FaceGeometry> face(SurfaceHostId host, Face face);

    /** Polygon providers can override this for folded meshes, concave faces and explicit cut proxies. */
    default Optional<SurfaceGeometry> geometry(SurfaceHostId host) {
        var triangles = new ArrayList<Triangle>();
        for (Face side : Face.values()) face(host, side).ifPresent(polygon -> {
            var v = polygon.vertices();
            for (int i = 2; i < v.size(); i++) triangles.add(new Triangle(v.getFirst(),v.get(i-1),v.get(i)));
        });
        return triangles.isEmpty() ? Optional.empty() : Optional.of(new SurfaceGeometry(new SurfaceMesh(triangles)));
    }
}
