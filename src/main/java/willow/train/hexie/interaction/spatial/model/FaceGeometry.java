package willow.train.hexie.interaction.spatial.model;

import java.util.List;

/** Ordered polygon vertices relative to the queried cell, with outward-facing winding. */
public record FaceGeometry(List<Vec> vertices) {
    public FaceGeometry {
        vertices = List.copyOf(vertices);
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("A face requires at least three vertices");
        }
    }
}
