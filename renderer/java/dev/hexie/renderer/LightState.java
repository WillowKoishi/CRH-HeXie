package dev.hexie.renderer;

public record LightState(long lightId, long ownerGroup, long attachment, Vec localPosition,
                         Vec linearRgb, double intensity, double radius, boolean enabled,
                         LightKind kind, double innerCone, double outerCone) {
    public enum LightKind { POINT, SPOT }
    public enum Support { SUPPORTED, UNSUPPORTED }

    public LightState {
        java.util.Objects.requireNonNull(kind);
        java.util.Objects.requireNonNull(localPosition);
        if (!(Double.isFinite(radius) && radius > 0 && Double.isFinite(intensity) && intensity >= 0)
            || Math.min(linearRgb.x(), Math.min(linearRgb.y(),linearRgb.z())) < 0)
            throw new IllegalArgumentException("light parameters");
    }
    public Support support(double radiusLimit) {
        return kind == LightKind.POINT && radius <= radiusLimit ? Support.SUPPORTED : Support.UNSUPPORTED;
    }
    /** Scalar linear contribution before colour, BRDF and cosine. r0 is one block. */
    public double energy(double distance, Visibility visibility, double radiusLimit) {
        if (support(radiusLimit) != Support.SUPPORTED) throw new UnsupportedOperationException("unsupported light");
        if (!enabled || visibility != Visibility.VISIBLE || distance >= radius) return 0;
        double t = Math.max(0, 15*(distance/radius)-14);
        return intensity*(1-t*t*(3-2*t))/Math.max(distance*distance,1);
    }
}
