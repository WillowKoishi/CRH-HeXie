package willow.train.hexie.interaction.spatial.model;

public record AABB(Vec min, Vec max) {
    public AABB {
        if (!(min.x() <= max.x() && min.y() <= max.y() && min.z() <= max.z())) {
            throw new IllegalArgumentException("Unordered bounds");
        }
    }
}
