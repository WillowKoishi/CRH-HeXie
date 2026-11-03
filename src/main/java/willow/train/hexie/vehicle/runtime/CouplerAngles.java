package willow.train.hexie.vehicle.runtime;

import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.BodyPose;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.EndpointPose;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Hook-local swing from the registered arm direction, in radians; the axis permits yaw only. */
public record CouplerAngles(double yaw, double pitch) {
    public static final CouplerAngles ZERO = new CouplerAngles(0, 0);

    public CouplerAngles {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch))
            throw new IllegalArgumentException("Non-finite coupler angles");
    }

    /** Positive yaw turns toward local -X. Pitch remains zero relative to the carriage. */
    public static CouplerAngles from(CouplingEndpoint endpoint, BodyPose body, EndpointPose pose) {
        var actual = pose.complete().subtract(pose.pivot());
        Point pivot = endpoint.axisPoint(), complete = endpoint.effectiveComplete();
        double restX = complete.x() - pivot.x(), restZ = complete.z() - pivot.z();
        if (restX * restX + restZ * restZ < 1e-24) return ZERO;
        // Remove both the solved body rotation and the hook's registered block facing.
        double x = actual.dot(body.rotate(endpoint.rotated(new Point(1, 0, 0))));
        double z = actual.dot(body.rotate(endpoint.rotated(new Point(0, 0, 1))));
        if (x * x + z * z < 1e-24) return ZERO;
        return new CouplerAngles(wrap(Math.atan2(-x, -z) - Math.atan2(-restX, -restZ)), 0);
    }

    public CouplerAngles interpolate(CouplerAngles next, double fraction) {
        if (fraction >= 1) return next;
        return new CouplerAngles(yaw + wrap(next.yaw - yaw) * fraction,
                pitch + (next.pitch - pitch) * fraction);
    }

    private static double wrap(double angle) { return Math.atan2(Math.sin(angle), Math.cos(angle)); }
}
