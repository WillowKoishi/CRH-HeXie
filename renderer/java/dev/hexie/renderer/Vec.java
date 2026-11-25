package dev.hexie.renderer;

public record Vec(double x, double y, double z) {
    public static final Vec ZERO = new Vec(0, 0, 0);
    public Vec {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("non-finite position");
    }
    public double at(int axis) {
        return switch (axis) { case 0 -> x; case 1 -> y; case 2 -> z; default -> throw new IndexOutOfBoundsException(); };
    }
    public Vec plus(Vec v) { return new Vec(x + v.x, y + v.y, z + v.z); }
    public Vec minus(Vec v) { return new Vec(x - v.x, y - v.y, z - v.z); }
    public Vec times(double s) { return new Vec(x * s, y * s, z * s); }
    public double dot(Vec v) { return x * v.x + y * v.y + z * v.z; }
    public Vec cross(Vec v) { return new Vec(y*v.z-z*v.y, z*v.x-x*v.z, x*v.y-y*v.x); }
    public static Vec min(Vec a, Vec b) {
        return new Vec(Math.min(a.x,b.x), Math.min(a.y,b.y), Math.min(a.z,b.z));
    }
    public static Vec max(Vec a, Vec b) {
        return new Vec(Math.max(a.x,b.x), Math.max(a.y,b.y), Math.max(a.z,b.z));
    }
}
