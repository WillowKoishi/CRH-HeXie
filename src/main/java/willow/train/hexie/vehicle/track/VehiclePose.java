package willow.train.hexie.vehicle.track;

import java.util.Objects;

/** Geometric body/support poses; no mass, wheel contact solver, or rendered-block mutation. */
public record VehiclePose(TrackGeometry.Vector position, double yawRadians, double pitchRadians,
                          double rollRadians, Support rear, Support front) {
    private static final int MAX_RIGID_SEARCH_STEPS = 4096;

    /**
     * Place a rigid body from one reference support. The other support is the first
     * point in the chosen direction at the registered straight-line wheelbase.
     * A curve therefore changes the supports' arc-length separation, never the body scale.
     * Facing sign describes the body's physical front, not its current velocity: keep
     * it unchanged while reversing. Exhausting the search budget is not a proof that
     * the entire route has no solution; callers must retain their last valid placement.
     */
    public static VehiclePose rigidOnRoute(TrackRoute route, double rearDistance, double nominalWheelbase,
                                           int facingSign) {
        return rigidOnRoute(route, rearDistance, nominalWheelbase, facingSign, AttitudePolicy.FOLLOW_TRACK);
    }

    public static VehiclePose rigidOnRoute(TrackRoute route, double rearDistance, double nominalWheelbase,
                                           int facingSign, AttitudePolicy policy) {
        Objects.requireNonNull(route);
        Objects.requireNonNull(policy);
        if (!Double.isFinite(nominalWheelbase) || nominalWheelbase <= 0)
            throw new IllegalArgumentException("Rigid wheelbase must be positive and finite");
        if (facingSign != 1 && facingSign != -1)
            throw new IllegalArgumentException("Facing sign must be +1 or -1");
        TrackGeometry.Vector reference = route.sample(rearDistance).position();
        double limit = facingSign > 0 ? route.length() : 0;
        double tolerance = Math.min(1e-5, nominalWheelbase * 1e-6);
        double distance = rearDistance;
        double gap = nominalWheelbase;
        for (int step = 0; step < MAX_RIGID_SEARCH_STEPS; step++) {
            if (distance == limit)
                throw new IllegalArgumentException("No rigid placement before the chosen route ends");
            // Chord distance cannot grow faster than travelled arc length. Advancing
            // by less than the remaining gap cannot skip an earlier sphere crossing.
            double advance = Math.max(tolerance * .25, Math.min(nominalWheelbase * .5, gap * .75));
            double next = facingSign > 0 ? Math.min(limit, distance + advance) : Math.max(limit, distance - advance);
            if (next == distance)
                throw new IllegalArgumentException("Rigid placement exceeds route distance precision");
            TrackGeometry.Vector delta = route.sample(next).position().subtract(reference);
            double nextGap = nominalWheelbase - Math.sqrt(delta.dot(delta));
            if (Math.abs(nextGap) <= tolerance)
                return onRoute(route, rearDistance, next, policy);
            if (nextGap < 0) {
                // Sampling-map roundoff can step just beyond the crossing. Solve on
                // this already bounded interval instead of moving either body anchor.
                double before = distance;
                double after = next;
                for (int refinement = 0; refinement < 64; refinement++) {
                    double middle = before + (after - before) * .5;
                    delta = route.sample(middle).position().subtract(reference);
                    double middleGap = nominalWheelbase - Math.sqrt(delta.dot(delta));
                    if (Math.abs(middleGap) <= tolerance)
                        return onRoute(route, rearDistance, middle, policy);
                    if (middleGap > 0) before = middle;
                    else after = middle;
                }
                throw new IllegalArgumentException("Rigid placement exceeds chord precision");
            }
            distance = next;
            gap = nextGap;
        }
        throw new IllegalArgumentException("No rigid placement within the bounded route search");
    }

    /** Angles use Minecraft's convention: yaw zero faces +Z, positive yaw faces -X; positive pitch looks down. */
    public static VehiclePose onRoute(TrackRoute route, double rearDistance, double frontDistance) {
        return onRoute(route, rearDistance, frontDistance, AttitudePolicy.FOLLOW_TRACK);
    }

    public static VehiclePose onRoute(TrackRoute route, double rearDistance, double frontDistance,
                                      AttitudePolicy policy) {
        Objects.requireNonNull(route);
        Objects.requireNonNull(policy);
        if (rearDistance == frontDistance) throw new IllegalArgumentException("Supports must be distinct");
        TrackGeometry.Sample rearSample = route.sample(rearDistance);
        TrackGeometry.Sample frontSample = route.sample(frontDistance);
        if (frontDistance < rearDistance) {
            rearSample = TrackRoute.reverse(rearSample);
            frontSample = TrackRoute.reverse(frontSample);
        }
        TrackGeometry.Vector forward = frontSample.position().subtract(rearSample.position()).normalized();
        TrackGeometry.Vector midpoint = rearSample.position().subtract(rearSample.position()
                .subtract(frontSample.position()).scale(.5));
        BodyAdjustment adjustment = Objects.requireNonNull(policy.adjust(rearSample, frontSample));
        TrackGeometry.Vector centre = midpoint.subtract(adjustment.offset().scale(-1));
        return new VehiclePose(centre, yaw(forward), pitch(forward), adjustment.rollRadians(),
                support(rearDistance, rearSample), support(frontDistance, frontSample));
    }

    /** Each support follows its own track tangent, independently of the body's chord direction. */
    public record Support(double routeDistance, TrackGeometry.Sample track, double yawRadians, double pitchRadians) {}

    /** World-space body offset and body roll; supports remain on their independently sampled geometry. */
    public record BodyAdjustment(TrackGeometry.Vector offset, double rollRadians) {
        public BodyAdjustment {
            Objects.requireNonNull(offset);
            if (!Double.isFinite(rollRadians)) throw new IllegalArgumentException("Roll must be finite");
        }
    }

    /** Replace for future tilt/suspension animation; existing Create geometry provides zero cant. */
    @FunctionalInterface
    public interface AttitudePolicy {
        AttitudePolicy FOLLOW_TRACK = (rear, front) -> new BodyAdjustment(new TrackGeometry.Vector(0, 0, 0),
                (rear.cantRadians() + front.cantRadians()) * .5);
        BodyAdjustment adjust(TrackGeometry.Sample rear, TrackGeometry.Sample front);
    }

    private static Support support(double distance, TrackGeometry.Sample sample) {
        return new Support(distance, sample, yaw(sample.tangent()), pitch(sample.tangent()));
    }

    private static double yaw(TrackGeometry.Vector direction) { return Math.atan2(-direction.x(), direction.z()); }
    private static double pitch(TrackGeometry.Vector direction) {
        return Math.atan2(-direction.y(), Math.hypot(direction.x(), direction.z()));
    }
}
