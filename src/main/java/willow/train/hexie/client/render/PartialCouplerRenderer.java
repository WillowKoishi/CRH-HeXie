package willow.train.hexie.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.List;
import java.util.function.Function;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import willow.train.hexie.compat.create.SnapshotCouplerAnimation;

/** Model parameters for the existing coupler render pass, shared by rigid arms and body-mounted bellows. */
public final class PartialCouplerRenderer implements SnapshotCouplerRenderer.Renderer {
    /** Authored lower/upper contact edges; all side parts inherit the solution between them. */
    public record ContactSpan(double lowerY, double upperY) {
        public ContactSpan {
            if (!Double.isFinite(lowerY) || !Double.isFinite(upperY) || lowerY >= upperY)
                throw new IllegalArgumentException("Invalid bellows contact edges");
        }
    }
    /** Model coordinates are relative to the block corner, facing north; frontZ must be less than baseZ. */
    public record BellowsPart(PartialModel model, double baseZ, double frontZ, double maxThickness) {
        public BellowsPart {
            if (!(frontZ < baseZ)) throw new IllegalArgumentException("Bellows must extend toward local north");
            if (!(maxThickness >= baseZ - frontZ))
                throw new IllegalArgumentException("Maximum thickness must include the neutral bellows");
        }
    }

    private final PartialModel arm, base;
    private final Vec3 pivot;
    private final List<BellowsPart> bellows;
    private final double extensionShare;
    private final Function<BlockState, AABB> bounds;
    private final ContactSpan contactSpan;

    /** base may be null when the fixed shell already belongs to the carriage structure model. */
    public PartialCouplerRenderer(PartialModel arm, Vec3 modelPivot, PartialModel base,
            List<BellowsPart> bellows, double extensionShare) {
        this(arm, modelPivot, base, bellows, extensionShare, state -> null);
    }

    public PartialCouplerRenderer(PartialModel arm, Vec3 modelPivot, PartialModel base,
            List<BellowsPart> bellows, double extensionShare, Function<BlockState, AABB> bounds) {
        this(arm, modelPivot, base, bellows, extensionShare, bounds, null);
    }

    public PartialCouplerRenderer(PartialModel arm, Vec3 modelPivot, PartialModel base,
            List<BellowsPart> bellows, double extensionShare, Function<BlockState, AABB> bounds,
            ContactSpan contactSpan) {
        this.arm = java.util.Objects.requireNonNull(arm);
        this.pivot = java.util.Objects.requireNonNull(modelPivot);
        this.base = base;
        this.bellows = List.copyOf(bellows);
        if (!Double.isFinite(extensionShare) || extensionShare < 0)
            throw new IllegalArgumentException("Invalid bellows share of the coupling gap");
        this.extensionShare = extensionShare;
        this.bounds = java.util.Objects.requireNonNull(bounds);
        this.contactSpan = contactSpan;
    }

    @Override public AABB attachedBounds(BlockState state) { return bounds.apply(state); }

    @Override
    public void renderArm(BlockState state, SnapshotCouplerAnimation.Parameters parameters, float partialTick,
            PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        stack.pushPose();
        // The existing pass already applied the solved arm rotation and pivot position.
        stack.translate(-pivot.x, -pivot.y, -pivot.z);
        CachedBuffers.partial(arm, state).light(light).overlay(overlay)
                .renderInto(stack, buffers.getBuffer(RenderType.cutoutMipped()));
        stack.popPose();
    }

    @Override
    public void renderBase(BlockState state, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        if (base != null) CachedBuffers.partial(base, state).light(light).overlay(overlay)
                .renderInto(stack, buffers.getBuffer(RenderType.cutoutMipped()));
    }

    @Override
    public void renderAttachedParts(BlockState state, SnapshotCouplerAnimation.Parameters parameters,
            float partialTick, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        renderBellows(state, parameters, false, stack, buffers, light, overlay);
    }

    @Override
    public void renderUncoupledAttachedParts(BlockState state, PoseStack stack,
            MultiBufferSource buffers, int light, int overlay) {
        renderBellows(state, null, true, stack, buffers, light, overlay);
    }

    private void renderBellows(BlockState state, SnapshotCouplerAnimation.Parameters parameters, boolean uncoupled,
            PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        var consumer = buffers.getBuffer(RenderType.cutoutMipped());
        var modelPose = new PoseStack();
        BellowsDeformation deformation = null;
        for (var part : bellows) {
            // Parts with the same fixed depth share the upper/lower solution.
            if (deformation == null || deformation.baseZ() != part.baseZ()
                    || deformation.frontZ() != part.frontZ() || deformation.maxThickness() != part.maxThickness()) {
                deformation = (uncoupled ? BellowsDeformation.free(part.baseZ(), part.frontZ(), part.maxThickness())
                        : BellowsDeformation.of(pivot, part.baseZ(), part.frontZ(), part.maxThickness(), parameters, extensionShare))
                        .withContactSpan(contactSpan);
            }
            // Keep the cached source vertices in model space. Apply the existing world/body pose
            // only after changing depth, so facing and carriage rotation do not alter the deformation.
            CachedBuffers.partial(part.model(), state).light(light).overlay(overlay)
                    .renderInto(modelPose, new DeformingConsumer(consumer, stack.last(), deformation));
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
                SnapshotCouplerAnimation.Parameters parameters, double extensionShare) {
            if (parameters == null) return new BellowsDeformation(baseZ, frontZ, maxThickness, 0, 0, 0);
            if (!parameters.coupled()) return free(baseZ, frontZ, maxThickness);
            var partner = parameters.partnerPose();
            if (partner != null) {
                // Both ends use the same angle-bisector plane between their fixed mounting
                // planes. The mate pose already contains the two bodies' solved positions,
                // inclines and roll; the coupler's independent pivot is not a bellows mount.
                var mateNormal = new org.joml.Vector3d(0, 0, -1)
                        .rotate(new org.joml.Quaterniond(partner.rotation()).normalize());
                var mateMount = partner.transform(new willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point(0, 0, baseZ));
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
    }

    private static final class DeformingConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final PoseStack.Pose pose;
        private final BellowsDeformation deformation;
        private final Vector3f normal = new Vector3f();
        private float x, y, z;

        private DeformingConsumer(VertexConsumer delegate, PoseStack.Pose pose, BellowsDeformation deformation) {
            this.delegate = delegate; this.pose = pose; this.deformation = deformation;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
            delegate.addVertex(pose.pose(), x, y, (float) deformation.depth(x, y, z));
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float nx, float ny, float nz) {
            double length = deformation.frontZ() - deformation.baseZ();
            double fraction = Math.max(0, Math.min(1, (z - deformation.baseZ()) / length));
            double front = deformation.front(x, y);
            double scale = Math.max(1e-6, (front - deformation.baseZ()) / length);
            // Inverse-transpose of the depth deformation's Jacobian, followed by the body normal matrix.
            normal.set((float) (nx - fraction * deformation.frontSlopeX(x, y) * nz / scale),
                    (float) (ny - fraction * deformation.frontSlopeY(x, y) * nz / scale), (float) (nz / scale));
            pose.normal().transform(normal);
            normal.normalize();
            delegate.setNormal(normal.x, normal.y, normal.z);
            return this;
        }
    }
}
