package willow.train.hexie.compat.create.motion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationAdapterAttachmentView;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationParametersView;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationPartnerPoseView;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationView;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplerAdapterView;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplerAnglesView;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplingEndpointFacing;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplingEndpointKind;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplingEndpointRenderStateView;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplingEndpointVisualView;
import willow.train.hexie.bridge.vehicle.CouplingEndpointView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageDefinitionView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleConstraintSolverBodyPoseView;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.core.api.vehicle.Point3;

/** Independent rigid coupler arms, driven by the exact endpoint poses used by the constraint solver. */
public final class SnapshotCouplerAnimation implements SnapshotCouplerAnimationView {
    private final SnapshotCouplerAnimationNbt animationNbt = new SnapshotCouplerAnimationNbt(this);

    public CompoundTag write() {
        return animationNbt.write();
    }

    public void read(CompoundTag root, int steps) {
        animationNbt.read(root, steps);
    }

    public static State decode(CompoundTag root) {
        return SnapshotCouplerAnimationNbt.decode(root);
    }

    /** Original model-space attachment; the adapter follows the rigid arm without stretching it. */
    public record AdapterAttachment(CouplerAdapterView adapter, Point3 pivot, Point3 complete, boolean reversed) implements SnapshotCouplerAnimationAdapterAttachmentView {
        public AdapterAttachment {
            Objects.requireNonNull(adapter);
            Objects.requireNonNull(pivot);
            Objects.requireNonNull(complete);
        }
    }

    /** Partner's north-facing block-corner model space, expressed in this hook's same model space. */
    public record PartnerPose(UUID endpoint, Point3 origin, float qx, float qy, float qz, float qw,
                              CouplingEndpointRenderStateView state) implements SnapshotCouplerAnimationPartnerPoseView {
        public PartnerPose(UUID endpoint, Point3 origin, float qx, float qy, float qz, float qw) {
            this(endpoint, origin, qx, qy, qz, qw, null);
        }
        public PartnerPose {
            Objects.requireNonNull(endpoint);
            Objects.requireNonNull(origin);
            double length = Math.sqrt((double) qx * qx + (double) qy * qy + (double) qz * qz + (double) qw * qw);
            if (!Double.isFinite(length) || length < 1e-12)
                throw new IllegalArgumentException("Invalid partner orientation");
            qx = (float) (qx / length); qy = (float) (qy / length);
            qz = (float) (qz / length); qw = (float) (qw / length);
        }

        public Quaternionf rotation() { return new Quaternionf(qx, qy, qz, qw); }

        public Point3 transform(Point3 point) {
            var rotated = rotation().transform(new Vector3f((float) point.x(), (float) point.y(), (float) point.z()));
            return new Point3(origin.x() + rotated.x, origin.y() + rotated.y, origin.z() + rotated.z);
        }

        public PartnerPose interpolate(PartnerPose next, float fraction) {
            if (next == null || fraction >= 1 || !endpoint.equals(next.endpoint)) return next;
            var rotation = rotation().slerp(next.rotation(), fraction);
            return new PartnerPose(endpoint, new Point3(origin.x() + (next.origin.x() - origin.x()) * fraction,
                    origin.y() + (next.origin.y() - origin.y()) * fraction,
                    origin.z() + (next.origin.z() - origin.z()) * fraction),
                    rotation.x, rotation.y, rotation.z, rotation.w, next.state);
        }

        /** Use the two fixed body mounts, never the independently swinging coupler pivot. */
        public static PartnerPose between(CouplingEndpointView own, VehicleConstraintSolverBodyPoseView ownBody,
                CouplingEndpointView partner, VehicleConstraintSolverBodyPoseView partnerBody) {
            Quaternionf ownRotation = orientation(ownBody).rotateY(facingYaw(own.facing()));
            Quaternionf inverseOwn = new Quaternionf(ownRotation).conjugate();
            Quaternionf relative = new Quaternionf(inverseOwn)
                    .mul(orientation(partnerBody).rotateY(facingYaw(partner.facing()))).normalize();
            var delta = partnerBody.transform(partner.blockCenter()).subtract(ownBody.transform(own.blockCenter()));
            var localDelta = inverseOwn.transform(new Vector3f((float) delta.x(), (float) delta.y(), (float) delta.z()));
            var rotatedCenter = relative.transform(new Vector3f(.5f, .5f, .5f));
            return new PartnerPose(partner.id(), new Point3(.5 + localDelta.x - rotatedCenter.x,
                    .5 + localDelta.y - rotatedCenter.y, .5 + localDelta.z - rotatedCenter.z),
                    relative.x, relative.y, relative.z, relative.w,
                    partner.visual() == null ? null : partner.visual().fixed());
        }
    }

    /** Shared head gap in blocks, with this hook's angles relative to its registered resting direction. */
    public record Parameters(UUID endpoint, BlockPos position, double extension, CouplerAnglesView angles,
                             AdapterAttachment attachment, boolean coupled, PartnerPose partnerPose,
                             CouplingEndpointVisualView visual) implements SnapshotCouplerAnimationParametersView {
        public Parameters {
            Objects.requireNonNull(endpoint);
            position = position.immutable();
            Objects.requireNonNull(angles);
            if (!Double.isFinite(extension) || extension < 0)
                throw new IllegalArgumentException("Invalid coupler extension");
            if (!coupled) partnerPose = null;
        }

        public Parameters(UUID endpoint, BlockPos position, double extension, CouplerAnglesView angles) {
            this(endpoint, position, extension, angles, null, true);
        }

        /** Older callers/payloads already describe the rendered arm; retain their previous deformation. */
        public Parameters(UUID endpoint, BlockPos position, double extension, CouplerAnglesView angles,
                AdapterAttachment attachment) {
            this(endpoint, position, extension, angles, attachment, true);
        }

        public Parameters(UUID endpoint, BlockPos position, double extension, CouplerAnglesView angles,
                AdapterAttachment attachment, boolean coupled) {
            this(endpoint, position, extension, angles, attachment, coupled, null);
        }

        public Parameters(UUID endpoint, BlockPos position, double extension, CouplerAnglesView angles,
                AdapterAttachment attachment, boolean coupled, PartnerPose partnerPose) {
            this(endpoint, position, extension, angles, attachment, coupled, partnerPose, null);
        }

        public Parameters interpolate(Parameters next, float fraction) {
            if (fraction >= 1 || !endpoint.equals(next.endpoint) || !position.equals(next.position)) return next;
            return new Parameters(endpoint, position, extension + (next.extension - extension) * fraction,
                    angles.interpolate(next.angles, fraction), next.attachment, next.coupled,
                    partnerPose == null ? next.partnerPose : partnerPose.interpolate(next.partnerPose, fraction), next.visual);
        }
    }

    final SnapshotBogeyAnimation poses = new SnapshotBogeyAnimation();
    private List<SnapshotBogeyAnimation.Frame> indexedFrames = List.of();
    private final java.util.Map<BlockPos, Integer> frameIndices = new HashMap<>();
    private CarriageDefinitionView sourceDefinition;
    private Direction sourceForward;
    private List<Arm> arms = List.of();
    List<Parameters> currentParameters = List.of(), previousParameters = List.of(), targetParameters = List.of();
    int parameterInterpolationSteps;
    double floorOffset;

    public record State(List<SnapshotBogeyAnimation.Frame> poses, List<Parameters> parameters, double floorOffset) {
        public State interpolate(State next, float fraction) {
            if (fraction <= 0) return this;
            if (fraction >= 1) return next;
            var hooks = next.parameters;
            if (parameters.size() == hooks.size()) {
                var interpolated = new ArrayList<Parameters>(hooks.size());
                for (int i = 0; i < hooks.size(); i++) interpolated.add(parameters.get(i).interpolate(hooks.get(i), fraction));
                hooks = List.copyOf(interpolated);
            }
            return new State(SnapshotBogeyAnimation.interpolate(poses, next.poses, fraction), hooks, next.floorOffset);
        }
    }

    /** Uses the parent's confirmed timeline; no separate animation clock is advanced. */
    public void playback(State before, State now) {
        poses.playback(before.poses, now.poses);
        previousParameters = before.parameters;
        currentParameters = targetParameters = now.parameters;
        floorOffset = now.floorOffset;
        parameterInterpolationSteps = 0;
    }

    public List<SnapshotBogeyAnimation.Frame> frames() { return poses.frames(); }
    public SnapshotBogeyAnimation.Frame frame(int index, float partialTick) { return poses.frame(index, partialTick); }
    /** Index the compact pose list once per replacement, not once per picked owner. */
    public SnapshotBogeyAnimation.Frame frameAt(BlockPos position, float partialTick) {
        int index = frameIndex(position);
        return index < 0 ? null : frame(index, partialTick);
    }
    private int frameIndex(BlockPos position) {
        var current = frames();
        if (indexedFrames != current) {
            frameIndices.clear();
            for (int index = 0; index < current.size(); index++)
                frameIndices.put(current.get(index).position(), index);
            indexedFrames = current;
        }
        return frameIndices.getOrDefault(position, -1);
    }
    public List<Parameters> parameters() { return currentParameters; }
    public double floorOffset() { return floorOffset; }
    public CouplingEndpointVisualView visual(BlockPos position) {
        for (var parameter : currentParameters) if (parameter.position().equals(position)) return parameter.visual();
        return null;
    }
    public boolean hasShiftedBase(BlockPos position) {
        return floorOffset != 0 && frameIndex(position) >= 0;
    }
    public Parameters parameters(int index, float partialTick) {
        return previousParameters.size() == currentParameters.size()
                ? previousParameters.get(index).interpolate(currentParameters.get(index), partialTick) : currentParameters.get(index);
    }

    public void tick() {
        poses.tick();
        previousParameters = currentParameters;
        if (parameterInterpolationSteps <= 0) return;
        if (parameterInterpolationSteps == 1) currentParameters = targetParameters;
        else {
            var next = new ArrayList<Parameters>(currentParameters.size());
            for (int i = 0; i < currentParameters.size(); i++)
                next.add(currentParameters.get(i).interpolate(targetParameters.get(i), 1f / parameterInterpolationSteps));
            currentParameters = List.copyOf(next);
        }
        parameterInterpolationSteps--;
    }

    public void update(VehicleRuntimeView runtime, CarriageSnapshotView snapshot, Direction forward) {
        if (snapshot == null) return;
        CarriageDefinitionView definition = runtime.definition(snapshot.id());
        if (definition.couplings().stream().anyMatch(endpoint -> endpoint.visual() == null)) {
            for (var block : snapshot.blocks()) {
                var id = HexieBridges.coupling().vehicleCouplingCatalog().endpointId(snapshot.id(), block.position());
                var endpoint = definition.couplings().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
                if (endpoint == null || endpoint.visual() != null) continue;
                var hook = HexieBridges.couplerBlock().couplerBlock().of(block.state());
                if (hook == null) continue;
                var captured = HexieBridges.coupling().couplerDataRegistry().capture(hook, block.state());
                // Old saves already own their geometry; only the previously absent render selection is recovered.
                runtime.captureCouplerVisual(id, HexieBridges.coupling().createCouplingEndpointVisual(captured.facing(), captured.fixed(),
                        captured.shiftedFixed(), captured.moving(), endpoint.axisPoint().plus(new Point3(0, definition.floorOffset(), 0)),
                        captured.movingOffset()));
            }
            definition = runtime.definition(snapshot.id());
        }
        floorOffset = definition.floorOffset();
        if (sourceDefinition != definition || sourceForward != forward) {
            // Convert once from the runtime's north-forward coordinates to the captured block coordinates.
            Quaternionf fromNormalized = new Quaternionf().rotationY(-sourceYaw(forward));
            var registered = new ArrayList<Arm>();
            for (CouplingEndpointView endpoint : definition.couplings()) {
                if (endpoint.kind() != CouplingEndpointKind.RIGID) continue;
                var centre = endpoint.blockCenter();
                Vector3f block = fromNormalized.transform(new Vector3f((float) centre.x(), (float) centre.y(), (float) centre.z()));
                registered.add(new Arm(endpoint, new BlockPos(Math.round(block.x), Math.round(block.y), Math.round(block.z))));
            }
            arms = List.copyOf(registered);
            sourceDefinition = definition;
            sourceForward = forward;
        }
        if (arms.isEmpty()) {
            poses.setFrames(List.of());
            setParameters(List.of());
            return;
        }
        var solution = runtime.placement();
        VehicleConstraintSolverBodyPoseView body = solution.bodies().get(snapshot.id());
        var partnerMounts = new HashMap<UUID, Mount>();
        for (var carriage : runtime.definitions()) {
            var carriageBody = solution.bodies().get(carriage.id());
            if (carriageBody != null) for (var endpoint : carriage.couplings())
                partnerMounts.put(endpoint.id(), new Mount(endpoint, carriageBody));
        }
        Quaternionf bodyRotation = orientation(body);
        Quaternionf inverseSourceBody = new Quaternionf(bodyRotation).rotateY(sourceYaw(forward)).conjugate();
        var next = new ArrayList<SnapshotBogeyAnimation.Frame>(arms.size());
        var nextParameters = new ArrayList<Parameters>(arms.size());
        for (Arm arm : arms) {
            CouplingEndpointView endpoint = arm.endpoint;
            var solved = solution.endpoints().get(endpoint.id());
            if (solved == null) continue;
            Quaternionf restingRotation = new Quaternionf(bodyRotation).rotateY(facingYaw(endpoint.facing()));
            var angles = HexieBridges.coupling().couplerAngles().from(endpoint, body, solved);
            // A vertical axis permits yaw in the body's own frame. Body pitch/roll still follow the track.
            Quaternionf relative = new Quaternionf(inverseSourceBody).mul(restingRotation).rotateY((float) angles.yaw());
            var offset = solved.pivot().subtract(body.position());
            // The solver may reference another height on the same vertical axis for an offset adapter.
            // Keep the original hook mesh and its adapter mounting face at the authored base height.
            var basePivot = endpoint.axis().atHeight(endpoint.complete().y());
            double adapterHeight = endpoint.axisPoint().y() - basePivot.y();
            if (adapterHeight != 0) offset = offset.subtract(body.rotate(new Point3(0, adapterHeight, 0)));
            Vector3f local = inverseSourceBody.transform(new Vector3f((float) offset.x(), (float) offset.y(), (float) offset.z()));
            // The frame origin is the registered pivot. Rendering subtracts the arm model's own pivot.
            next.add(new SnapshotBogeyAnimation.Frame(arm.position, local.x + .5f, local.y + .5f, local.z + .5f,
                    relative.x, relative.y, relative.z, relative.w, 0, 0));
            UUID partner = runtime.couplingPartner(endpoint.id());
            var gap = partner == null ? null : solved.complete().subtract(solution.endpoints().get(partner).complete());
            var partnerMount = partner == null ? null : partnerMounts.get(partner);
            var partnerPose = partnerMount == null ? null
                    : PartnerPose.between(endpoint, body, partnerMount.endpoint, partnerMount.body);
            nextParameters.add(new Parameters(endpoint.id(), arm.position, gap == null ? 0 : Math.sqrt(gap.dot(gap)),
                    angles, endpoint.adapter() == null ? null
                    : new AdapterAttachment(endpoint.adapter(), modelPoint(basePivot), modelPoint(endpoint.complete()),
                            !endpoint.model().equals(endpoint.adapter().firstModel())), partner != null, partnerPose, endpoint.visual()));
        }
        poses.setFrames(next);
        setParameters(nextParameters);
    }

    private void setParameters(List<Parameters> parameters) {
        previousParameters = currentParameters = targetParameters = List.copyOf(parameters);
        parameterInterpolationSteps = 0;
    }

    /** Runtime hook parameters compensate the body floor; attachment geometry remains the registered mesh. */
    private Point3 modelPoint(Point3 point) {
        return new Point3(point.x(), point.y() + floorOffset, point.z());
    }

    private static Quaternionf orientation(VehicleConstraintSolverBodyPoseView pose) {
        return new Quaternionf().rotationY((float) pose.yaw()).rotateX((float) pose.pitch()).rotateZ((float) pose.roll());
    }

    private static float sourceYaw(Direction forward) {
        return switch (forward) {
            case EAST -> (float) (Math.PI / 2);
            case SOUTH -> (float) Math.PI;
            case WEST -> (float) (-Math.PI / 2);
            default -> 0;
        };
    }

    private static float facingYaw(CouplingEndpointFacing facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> (float) (-Math.PI / 2);
            case SOUTH -> (float) Math.PI;
            case WEST -> (float) (Math.PI / 2);
        };
    }

    private record Arm(CouplingEndpointView endpoint, BlockPos position) {}
    private record Mount(CouplingEndpointView endpoint, VehicleConstraintSolverBodyPoseView body) {}
}
