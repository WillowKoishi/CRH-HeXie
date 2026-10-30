package willow.train.hexie.vehicle.runtime;

import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.BodyPose;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;
import willow.train.hexie.vehicle.track.TrackGeometry.Vector;

/** The hook's block cross-section, partitioned by its start, completion and pivot planes. */
public record CouplingRegion(Vector centre, Vector right, Vector up, Vector depth, double halfDepth) {
    public static CouplingRegion of(CouplingEndpoint hook, BodyPose body, double floorOffset, boolean engagement) {
        double front = engagement ? hook.start().z() - (hook.adapter() == null ? 0 : hook.adapter().thickness())
                : hook.effectiveComplete().z();
        double back = hook.pivot().z();
        Point middle = hook.blockCenter().plus(hook.rotated(new Point(0, -floorOffset, (front + back) * .5)));
        return new CouplingRegion(body.transform(middle), body.rotate(hook.rotated(new Point(1, 0, 0))),
                body.rotate(hook.rotated(new Point(0, 1, 0))), body.rotate(hook.rotated(new Point(0, 0, 1))),
                Math.abs(back - front) * .5);
    }

    /** Separating-axis test: full block width/height, never distance between two isolated points. */
    public boolean intersects(CouplingRegion other, double tolerance) {
        Vector difference = other.centre.subtract(centre);
        Vector[] a = {right, up, depth}, b = {other.right, other.up, other.depth};
        for (Vector axis : a) if (separated(other, difference, axis, tolerance)) return false;
        for (Vector axis : b) if (separated(other, difference, axis, tolerance)) return false;
        for (Vector first : a) for (Vector second : b) {
            Vector axis = new Vector(first.y() * second.z() - first.z() * second.y(),
                    first.z() * second.x() - first.x() * second.z(), first.x() * second.y() - first.y() * second.x());
            if (axis.dot(axis) > 1e-20 && separated(other, difference, axis.normalized(), tolerance)) return false;
        }
        return true;
    }

    private boolean separated(CouplingRegion other, Vector difference, Vector axis, double tolerance) {
        return Math.abs(difference.dot(axis)) > radius(axis) + other.radius(axis) + tolerance;
    }

    private double radius(Vector axis) {
        return .5 * (Math.abs(right.dot(axis)) + Math.abs(up.dot(axis))) + halfDepth * Math.abs(depth.dot(axis));
    }
}
