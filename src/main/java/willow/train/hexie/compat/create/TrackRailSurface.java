package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.track.BezierConnection;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.vehicle.track.TrackGeometry;
import willow.train.hexie.init.track.TrackMaterialInit;

/** Section of the rail head in world coordinates. Side points right along the curve. */
public final class TrackRailSurface {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double PIVOT = 2 / 16d;
    public static final double HALF_GAUGE = 12.075 / 16;
    public record Section(Vec3 center, Vec3 side, Vec3 heightAxis) {}

    private final Section start, end;
    private final double[] parameters;
    private final Section[] sections;
    private final Vec3 control1, control2, startCorrection, endCorrection;

    public TrackRailSurface(BezierConnection curve) {
        start = endpoint(curve, true);
        end = endpoint(curve, false);
        startCorrection = residual(curve, start, curve.starts.getFirst(), true);
        endCorrection = residual(curve, end, curve.starts.getSecond(), false);
        double handle = curve.getHandleLength();
        control1 = curve.starts.getFirst().add(curve.axes.getFirst().normalize().scale(handle));
        control2 = curve.starts.getSecond().add(curve.axes.getSecond().normalize().scale(handle));
        int count = Math.max(1, curve.getSegmentCount());
        parameters = new double[count + 1];
        sections = new Section[count + 1];
        boolean monotone = true;
        parameters[count] = 1;
        for (int i = 1; i < count; i++) {
            parameters[i] = curve.getSegmentT(i);
            monotone &= parameters[i] > parameters[i - 1] && parameters[i] < 1;
        }
        for (int i = 0; i <= count; i++) {
            if (!monotone) parameters[i] = (double) i / count;
            sections[i] = sectionAt(curve, parameters[i]);
        }
    }

    public Section section(int i) { return sections[i]; }
    public int count() { return sections.length - 1; }
    public double parameter(int i) { return parameters[i]; }

    public TrackGeometry.Sample contact(double t) {
        int i = java.util.Arrays.binarySearch(parameters, t);
        i = i < 0 ? -i - 2 : Math.min(i, count() - 1);
        Section a = sections[i], b = sections[i + 1];
        double f = (t - parameters[i]) / (parameters[i + 1] - parameters[i]);
        Vec3 tangent = b.center.subtract(a.center);
        return contact(a.center.lerp(b.center, f), tangent, tangent.cross(a.side.lerp(b.side, f)));
    }

    static TrackGeometry.Sample contact(Vec3 p, Vec3 tangent, Vec3 normal) {
        return new TrackGeometry.Sample(vector(p), vector(tangent), vector(normal), 0);
    }

    private static TrackGeometry.Vector vector(Vec3 p) {
        return new TrackGeometry.Vector(p.x, p.y, p.z);
    }

    public static Section endpoint(BezierConnection curve, boolean first) {
        Vec3 axis = curve.axes.get(first);
        Vec3 tangent = axis.normalize();
        Vec3 p = curve.starts.get(first);
        Vec3 base = Vec3.atBottomCenterOf(curve.bePositions.get(first));
        Vec3 normal = curve.normals.get(first);
        // A flexible block puts its graph endpoint half an axis from its bottom centre.
        // Create's rising block starts half a block higher and uses vertical section cuts.
        if (flexibleEnd(curve, first)) {
            Section section = CreateTrackCompatibility.flexibleSection(base, axis, normal, .5);
            return first ? section : new Section(section.center, section.side.scale(-1), section.heightAxis);
        }
        Vec3 side = UP.cross(tangent).normalize().scale(first ? 1 : -1);
        if (curve.smoothing != null && axis.y != 0 && normal.y == 1) {
            // Create's TrackModel tilts a level block around its low end at Y=1/4.
            // The saved high graph endpoint can also have a clamped gradient.
            Vec3 uphill = axis.y > 0 ? tangent : tangent.scale(-1);
            Vec3 horizontal = uphill.multiply(1, 0, 1).normalize();
            double span = Math.abs(horizontal.x * horizontal.z) > .25 ? Math.sqrt(2) : 1;
            Vec3 low = base.subtract(horizontal.scale(span / 2));
            Vec3 up = uphill.cross(UP.cross(uphill)).normalize();
            Vec3 rail = low.add(0, .25, 0).add(up.scale(TrackMaterialInit.RAIL_SURFACE_HEIGHT - .25));
            if (axis.y > 0) rail = rail.add(uphill.scale(span));
            return new Section(rail, side, up);
        }
        return new Section(p.add(0, TrackMaterialInit.RAIL_SURFACE_HEIGHT, 0), side, UP);
    }

    static boolean flexibleEnd(BezierConnection curve, boolean first) {
        Vec3 axis = curve.axes.get(first);
        Vec3 base = Vec3.atBottomCenterOf(curve.bePositions.get(first));
        return Math.abs(axis.lengthSqr() - 1) < 1e-5
                && base.add(axis.scale(.5)).distanceToSqr(curve.starts.get(first)) < 1e-8
                && CreateTrackCompatibility.hasRailx();
    }

    public static Section rotatedSection(Vec3 base, Vec3 tangent, Vec3 side, Vec3 normal, double along) {
        return new Section(base.add(0, PIVOT, 0).add(tangent.scale(along))
                .add(normal.scale(TrackMaterialInit.RAIL_SURFACE_HEIGHT - PIVOT)), side, normal);
    }

    private Section sectionAt(BezierConnection curve, double t) {
        if (t == 0) return start;
        if (t == 1) return end;
        Vec3 a = curve.starts.getFirst(), b = curve.starts.getSecond();
        Vec3 u = a.lerp(control1, t), v = control1.lerp(control2, t), w = control2.lerp(b, t);
        Vec3 first = u.lerp(v, t), second = v.lerp(w, t);
        Vec3 tangent = second.subtract(first).normalize();
        Vec3 normal = curve.getNormal(t);
        Vec3 side = normal.cross(tangent).normalize();
        // Transport the offset on the curve, with the authored straight section at each joint.
        Vec3 offset = normal.scale(TrackMaterialInit.RAIL_SURFACE_HEIGHT - PIVOT).add(0, PIVOT, 0);
        double blend = t * t * (3 - 2 * t);
        Vec3 correction = startCorrection.lerp(endCorrection, blend);
        return new Section(first.lerp(second, t).add(offset).add(correction), side, normal);
    }

    private static Vec3 residual(BezierConnection curve, Section section, Vec3 base, boolean first) {
        return section.center.subtract(base).subtract(curve.getNormal(first ? 0 : 1)
                .scale(TrackMaterialInit.RAIL_SURFACE_HEIGHT - PIVOT)).add(0, -PIVOT, 0);
    }
}
