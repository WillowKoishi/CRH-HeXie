package dev.hexie.renderer.parse;

import dev.hexie.renderer.StructureRevision;
import dev.hexie.renderer.parse.PlaneFrame.CardinalFace;
import dev.hexie.renderer.parse.PlaneFrame.UV;
import java.util.*;

/**
 * Parameterized custom window specification matching the user's tool parameters:
 * frameColor, glassColor, glassAlpha, frameThickness, cornerRadius, width, height, facing, offsetU, offsetV.
 */
public record ParametricWindowSpec(
        int frameColorRgb,
        int glassColorRgb,
        double glassAlpha,
        double frameThickness,
        double cornerRadius,
        double width,
        double height,
        CardinalFace facing,
        double offsetU,
        double offsetV
) {
    public static final double MIN_SIZE = 1.0 / 16.0;
    public static final int DEFAULT_CORNER_SEGMENTS = 6;

    public ParametricWindowSpec {
        Objects.requireNonNull(facing);
        frameColorRgb &= 0x00FFFFFF;
        glassColorRgb &= 0x00FFFFFF;
        if (!Double.isFinite(width) || width < MIN_SIZE) throw new IllegalArgumentException("width");
        if (!Double.isFinite(height) || height < MIN_SIZE) throw new IllegalArgumentException("height");
        if (!Double.isFinite(frameThickness) || frameThickness < 0) throw new IllegalArgumentException("frameThickness");
        if (!Double.isFinite(cornerRadius) || cornerRadius < 0) throw new IllegalArgumentException("cornerRadius");
        if (!Double.isFinite(glassAlpha) || glassAlpha < 0 || glassAlpha > 1) throw new IllegalArgumentException("glassAlpha");
        if (!Double.isFinite(offsetU) || !Double.isFinite(offsetV)) throw new IllegalArgumentException("offset");
    }

    public static ParametricWindowSpec defaults(CardinalFace facing) {
        return new ParametricWindowSpec(0x2B2F36, 0x8EC5E8, 0.38, 0.0625, 0.125, 0.875, 0.875, facing, 0.0, 0.0);
    }

    public double effectiveCornerRadius() {
        return Math.min(cornerRadius, Math.min(width, height) * 0.5 - 1e-4);
    }

    public double effectiveFrameThickness() {
        return Math.min(frameThickness, Math.min(width, height) * 0.5 - 1e-4);
    }

    public int frameArgb() { return 0xFF000000 | frameColorRgb; }

    public int glassArgb() {
        int a = Math.max(0, Math.min(255, (int) Math.round(glassAlpha * 255.0)));
        return (a << 24) | glassColorRgb;
    }

    public boolean hasGlass() {
        return width - 2.0 * effectiveFrameThickness() > 1e-3 && height - 2.0 * effectiveFrameThickness() > 1e-3;
    }

    /** Counter-clockwise convex 2D polygon on the facing plane for the outer opening contour. */
    public List<UV> outerLoop(int cornerSegments) {
        return roundedRect(offsetU, offsetV, width, height, Math.max(0.0, effectiveCornerRadius()), cornerSegments);
    }

    /** Counter-clockwise convex 2D polygon on the facing plane for the inner glass contour. */
    public List<UV> innerLoop(int cornerSegments) {
        double t = effectiveFrameThickness();
        double iw = width - 2.0 * t;
        double ih = height - 2.0 * t;
        if (iw <= 1e-4 || ih <= 1e-4) return List.of();
        double ir = Math.max(0.0, effectiveCornerRadius() - t);
        return roundedRect(offsetU, offsetV, iw, ih, Math.min(ir, Math.min(iw, ih) * 0.5 - 1e-5), cornerSegments);
    }

    public static List<UV> roundedRect(double cx, double cy, double w, double h, double radius, int cornerSegments) {
        double hw = w * 0.5;
        double hh = h * 0.5;
        double r = Math.max(0.0, Math.min(radius, Math.min(hw, hh)));
        if (r <= 1e-6 || cornerSegments <= 0) {
            return List.of(
                    new UV(cx - hw, cy - hh),
                    new UV(cx + hw, cy - hh),
                    new UV(cx + hw, cy + hh),
                    new UV(cx - hw, cy + hh));
        }
        int segs = Math.max(1, cornerSegments);
        var pts = new ArrayList<UV>(4 * (segs + 1));
        // 4 corners in CCW order: bottom-right, top-right, top-left, bottom-left
        double[] ccx = {cx + hw - r, cx + hw - r, cx - hw + r, cx - hw + r};
        double[] ccy = {cy - hh + r, cy + hh - r, cy + hh - r, cy - hh + r};
        double[] startAngle = {-Math.PI * 0.5, 0.0, Math.PI * 0.5, Math.PI};
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= segs; i++) {
                double ang = startAngle[c] + (Math.PI * 0.5) * i / segs;
                double px = ccx[c] + Math.cos(ang) * r;
                double py = ccy[c] + Math.sin(ang) * r;
                if (pts.isEmpty() || distSq(pts.get(pts.size() - 1), px, py) > 1e-12) {
                    pts.add(new UV(px, py));
                }
            }
        }
        if (pts.size() > 1 && distSq(pts.get(0), pts.get(pts.size() - 1).u(), pts.get(pts.size() - 1).v()) <= 1e-12) {
            pts.remove(pts.size() - 1);
        }
        return List.copyOf(pts);
    }

    private static double distSq(UV p, double x, double y) {
        double du = p.u() - x, dv = p.v() - y;
        return du * du + dv * dv;
    }

    public Map<String, Double> toParameters() {
        var map = new LinkedHashMap<String, Double>();
        map.put("frameColor", (double) frameColorRgb);
        map.put("glassColor", (double) glassColorRgb);
        map.put("glassAlpha", glassAlpha);
        map.put("frameThickness", frameThickness);
        map.put("cornerRadius", cornerRadius);
        map.put("width", width);
        map.put("height", height);
        map.put("offsetU", offsetU);
        map.put("offsetV", offsetV);
        return Collections.unmodifiableMap(map);
    }

    public static ParametricWindowSpec fromOpening(StructureRevision.OpeningRequest req) {
        var p = req.parameters();
        CardinalFace face = CardinalFace.fromNormal(req.inward().times(-1));
        return new ParametricWindowSpec(
                p.getOrDefault("frameColor", (double) 0x2B2F36).intValue(),
                p.getOrDefault("glassColor", (double) 0x8EC5E8).intValue(),
                p.getOrDefault("glassAlpha", 0.38),
                p.getOrDefault("frameThickness", 0.0625),
                p.getOrDefault("cornerRadius", 0.125),
                p.getOrDefault("width", 0.875),
                p.getOrDefault("height", 0.875),
                face,
                p.getOrDefault("offsetU", 0.0),
                p.getOrDefault("offsetV", 0.0));
    }
}
