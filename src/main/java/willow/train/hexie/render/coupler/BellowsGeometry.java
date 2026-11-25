package willow.train.hexie.render.coupler;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationParametersView;
import willow.train.hexie.core.api.vehicle.Point3;

// split from willow/train/hexie/client/render/PartialCouplerRenderer.java, lines 17-23, 93-186
public final class BellowsGeometry {
    /** Authored lower/upper contact edges; all side parts inherit the solution between them. */
    public record ContactSpan(double lowerY, double upperY) {
        public ContactSpan {
            if (!Double.isFinite(lowerY) || !Double.isFinite(upperY) || lowerY >= upperY)
                throw new IllegalArgumentException("Invalid bellows contact edges");
        }
    }

    /** Fixed body edge; the contact face is solved from both mounts without aligning the openings. */
    public record BellowsDeformation(double baseZ, double frontZ, double maxThickness, double deltaAtOrigin,
                                     double slopeX, double slopeY, ContactSpan contactSpan) {
        public BellowsDeformation(double baseZ, double frontZ, double maxThickness, double deltaAtOrigin,
                                 double slopeX, double slopeY) {
            this(baseZ, frontZ, maxThickness, deltaAtOrigin, slopeX, slopeY, null);
        }

        public BellowsDeformation withContactSpan(ContactSpan span) {
            return new BellowsDeformation(baseZ, frontZ, maxThickness, deltaAtOrigin, slopeX, slopeY, span);
        }

        public static BellowsDeformation free(double baseZ, double frontZ, double maxThickness) {
            return new BellowsDeformation(baseZ, frontZ, maxThickness, baseZ - maxThickness - frontZ, 0, 0);
        }

        public static BellowsDeformation of(Vec3 pivot, double baseZ, double frontZ, double maxThickness,
                SnapshotCouplerAnimationParametersView parameters, double extensionShare) {
            if (parameters == null) return new BellowsDeformation(baseZ, frontZ, maxThickness, 0, 0, 0);
            if (!parameters.coupled()) return free(baseZ, frontZ, maxThickness);
            var partner = parameters.partnerPose();
            if (partner != null) {
                // Both ends use the same angle-bisector plane between their fixed mounting
                // planes. The mate pose already contains the two bodies' solved positions,
                // inclines and roll; the coupler's independent pivot is not a bellows mount.
                var mateNormal = new org.joml.Vector3d(0, 0, -1)
                        .rotate(new org.joml.Quaterniond(partner.rotation()).normalize());
                var mateMount = partner.transform(new Point3(0, 0, baseZ));
                double nx = -mateNormal.x, ny = -mateNormal.y, nz = -1 - mateNormal.z;
                double distance = -baseZ - (mateNormal.x * mateMount.x()
                        + mateNormal.y * mateMount.y() + mateNormal.z * mateMount.z());
                // Facing planes parallel to the extrusion direction have no finite depth
                // intersection. Keep the free shape, rather than exceeding its reach.
                if (Math.abs(nz) < 1e-6) return free(baseZ, frontZ, maxThickness);
                return new BellowsDeformation(baseZ, frontZ, maxThickness,
                        distance / nz - frontZ, -nx / nz, -ny / nz);
            }
            // Older animation payloads have no partner mount. Preserve their previous behaviour.
            double yaw = parameters.angles().yaw(), pitch = parameters.angles().pitch();
            if (yaw == 0 && pitch == 0 && parameters.extension() == 0)
                return new BellowsDeformation(baseZ, frontZ, maxThickness, 0, 0, 0);
            double nx = -Math.sin(yaw) * Math.cos(pitch), ny = Math.sin(pitch);
            double nz = -Math.cos(yaw) * Math.cos(pitch);
            // A plane parallel to the depth axis has no single-valued z(x,y). Retain the source
            // shape at that singularity rather than introducing infinite vertices or a swing limit.
            if (Math.abs(nz) < 1e-6) return new BellowsDeformation(baseZ, frontZ, maxThickness, 0, 0, 0);
            double sx = -nx / nz, sy = -ny / nz;
            double planeZ = pivot.z + (pivot.z - frontZ + parameters.extension() * extensionShare) / nz;
            return new BellowsDeformation(baseZ, frontZ, maxThickness, planeZ - frontZ - sx * pivot.x - sy * pivot.y, sx, sy);
        }

        public double front(double x, double y) {
            if (contactSpan == null) return edge(x, y);
            // Solve the two horizontal edges first. Every inner/outer side vertex follows
            // those edges, including their reach limit; it never gets a separate angle.
            double lower = edge(x, contactSpan.lowerY()), upper = edge(x, contactSpan.upperY());
            double fraction = (y - contactSpan.lowerY()) / (contactSpan.upperY() - contactSpan.lowerY());
            // The outer skirt of the rubber extends below the inner bottom edge: extrapolate
            // the same solution there before limiting thickness, rather than introducing a step.
            return limit(lower + fraction * (upper - lower));
        }

        private double limit(double z) { return Math.max(baseZ - maxThickness, Math.min(baseZ, z)); }
        private double edge(double x, double y) { return limit(frontZ + deltaAtOrigin + slopeX * x + slopeY * y); }
        private boolean limited(double z) { return z <= baseZ - maxThickness || z >= baseZ; }
        public double frontSlopeX(double x, double y) {
            if (limited(front(x, y))) return 0;
            if (contactSpan == null) return slopeX;
            double fraction = (y - contactSpan.lowerY()) / (contactSpan.upperY() - contactSpan.lowerY());
            return (1 - fraction) * (limited(edge(x, contactSpan.lowerY())) ? 0 : slopeX)
                    + fraction * (limited(edge(x, contactSpan.upperY())) ? 0 : slopeX);
        }
        public double frontSlopeY(double x, double y) {
            if (limited(front(x, y))) return 0;
            if (contactSpan == null) return slopeY;
            return (edge(x, contactSpan.upperY()) - edge(x, contactSpan.lowerY()))
                    / (contactSpan.upperY() - contactSpan.lowerY());
        }

        public double depth(double x, double y, double z) {
            double fraction = Math.max(0, Math.min(1, (z - baseZ) / (frontZ - baseZ)));
            return z + fraction * (front(x, y) - frontZ);
        }

        void normal(float x, float y, float z, float nx, float ny, float nz, Vector3f normal) {
            double length = frontZ() - baseZ();
            double fraction = Math.max(0, Math.min(1, (z - baseZ()) / length));
            double front = front(x, y);
            double scale = Math.max(1e-6, (front - baseZ()) / length);
            // Inverse-transpose of the depth deformation's Jacobian, followed by the body normal matrix.
            normal.set((float) (nx - fraction * frontSlopeX(x, y) * nz / scale),
                    (float) (ny - fraction * frontSlopeY(x, y) * nz / scale), (float) (nz / scale));
        }
    }
}
