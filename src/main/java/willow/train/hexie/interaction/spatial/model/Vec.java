package willow.train.hexie.interaction.spatial.model;

public record Vec(double x, double y, double z) {
    public Vec add(Vec other) {
        return new Vec(x + other.x, y + other.y, z + other.z);
    }

    public Vec subtract(Vec other) {
        return new Vec(x - other.x, y - other.y, z - other.z);
    }

    public Vec scale(double factor) {
        return new Vec(x * factor, y * factor, z * factor);
    }

    public double length() {
        return Math.hypot(Math.hypot(x, y), z);
    }

    public Vec cross(Vec other) {
        return new Vec(y * other.z - z * other.y,
                       z * other.x - x * other.z,
                       x * other.y - y * other.x);
    }
}
