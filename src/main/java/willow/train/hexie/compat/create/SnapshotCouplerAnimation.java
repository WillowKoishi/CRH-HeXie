package willow.train.hexie.compat.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import willow.train.hexie.blocks.coupler.CouplerAdapterData;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.BodyPose;
import willow.train.hexie.vehicle.runtime.CarriageDefinition;
import willow.train.hexie.vehicle.runtime.CouplerAngles;
import willow.train.hexie.vehicle.runtime.CouplerAdapter;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Independent rigid coupler arms, driven by the exact endpoint poses used by the constraint solver. */
public final class SnapshotCouplerAnimation {
    /** Original model-space attachment; the adapter follows the rigid arm without stretching it. */
    public record AdapterAttachment(CouplerAdapter adapter, Point pivot, Point complete, boolean reversed) {
        public AdapterAttachment {
            Objects.requireNonNull(adapter);
            Objects.requireNonNull(pivot);
            Objects.requireNonNull(complete);
        }
    }

    /** Shared head gap in blocks, with this hook's angles relative to its registered resting direction. */
    public record Parameters(UUID endpoint, BlockPos position, double extension, CouplerAngles angles,
                             AdapterAttachment attachment) {
        public Parameters {
            Objects.requireNonNull(endpoint);
            position = position.immutable();
            Objects.requireNonNull(angles);
            if (!Double.isFinite(extension) || extension < 0)
                throw new IllegalArgumentException("Invalid coupler extension");
        }

        public Parameters(UUID endpoint, BlockPos position, double extension, CouplerAngles angles) {
            this(endpoint, position, extension, angles, null);
        }

        public Parameters interpolate(Parameters next, float fraction) {
            if (fraction >= 1 || !endpoint.equals(next.endpoint) || !position.equals(next.position)) return next;
            return new Parameters(endpoint, position, extension + (next.extension - extension) * fraction,
                    angles.interpolate(next.angles, fraction), next.attachment);
        }
    }

    private final SnapshotBogeyAnimation poses = new SnapshotBogeyAnimation();
    private CarriageDefinition sourceDefinition;
    private Direction sourceForward;
    private List<Arm> arms = List.of();
    private List<Parameters> currentParameters = List.of(), previousParameters = List.of(), targetParameters = List.of();
    private int parameterInterpolationSteps;
    private double floorOffset;

    public List<SnapshotBogeyAnimation.Frame> frames() { return poses.frames(); }
    public SnapshotBogeyAnimation.Frame frame(int index, float partialTick) { return poses.frame(index, partialTick); }
    public List<Parameters> parameters() { return currentParameters; }
    public double floorOffset() { return floorOffset; }
    public boolean hasShiftedBase(BlockPos position) {
        if (floorOffset == 0) return false;
        for (var frame : frames()) if (frame.position().equals(position)) return true;
        return false;
    }
    public Parameters parameters(int index, float partialTick) {
        return previousParameters.size() == currentParameters.size()
                ? previousParameters.get(index).interpolate(currentParameters.get(index), partialTick) : currentParameters.get(index);
    }

    public CompoundTag write() {
        CompoundTag root = poses.write();
        if (floorOffset != 0) root.putDouble("FloorOffset", floorOffset);
        var hooks = new ListTag();
        for (var parameters : currentParameters) {
            var tag = new CompoundTag();
            tag.putUUID("Id", parameters.endpoint());
            tag.putLong("Pos", parameters.position().asLong());
            tag.putDouble("Extension", parameters.extension());
            tag.putDouble("Yaw", parameters.angles().yaw());
            tag.putDouble("Pitch", parameters.angles().pitch());
            if (parameters.attachment() != null) {
                var attachment = parameters.attachment();
                var adapter = new CompoundTag();
                CouplerAdapterData.write(adapter, attachment.adapter());
                adapter.putBoolean("Reversed", attachment.reversed());
                writePoint(adapter, "Pivot", attachment.pivot());
                writePoint(adapter, "Complete", attachment.complete());
                tag.put("Adapter", adapter);
            }
            hooks.add(tag);
        }
        root.put("Hooks", hooks);
        return root;
    }

    public void read(CompoundTag root, int steps) {
        poses.read(root, steps);
        floorOffset = root.getDouble("FloorOffset");
        var decoded = new ArrayList<Parameters>();
        for (Tag value : root.getList("Hooks", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) value;
            if (!tag.hasUUID("Id") || !tag.contains("Pos", Tag.TAG_LONG)
                    || !tag.contains("Extension", Tag.TAG_DOUBLE) || !tag.contains("Yaw", Tag.TAG_DOUBLE)
                    || !tag.contains("Pitch", Tag.TAG_DOUBLE)) continue;
            AdapterAttachment attachment = null;
            if (tag.contains("Adapter", Tag.TAG_COMPOUND)) {
                var adapter = tag.getCompound("Adapter");
                var definition = CouplerAdapterData.read(adapter);
                if (definition != null) attachment = new AdapterAttachment(definition,
                        readPoint(adapter, "Pivot"), readPoint(adapter, "Complete"), adapter.getBoolean("Reversed"));
            }
            decoded.add(new Parameters(tag.getUUID("Id"), BlockPos.of(tag.getLong("Pos")), tag.getDouble("Extension"),
                    new CouplerAngles(tag.getDouble("Yaw"), tag.getDouble("Pitch")), attachment));
        }
        targetParameters = List.copyOf(decoded);
        boolean sameHooks = currentParameters.size() == targetParameters.size();
        for (int i = 0; sameHooks && i < currentParameters.size(); i++) {
            var current = currentParameters.get(i);
            var target = targetParameters.get(i);
            sameHooks = current.endpoint().equals(target.endpoint()) && current.position().equals(target.position());
        }
        // Legacy animation payloads still supply poses; their absent hook parameters stay empty.
        if (!sameHooks || steps <= 0) previousParameters = currentParameters = targetParameters;
        parameterInterpolationSteps = Math.max(1, steps);
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

    public void update(VehicleRuntime runtime, CarriageSnapshot snapshot, Direction forward) {
        if (snapshot == null) return;
        CarriageDefinition definition = runtime.definition(snapshot.id());
        floorOffset = definition.floorOffset();
        if (sourceDefinition != definition || sourceForward != forward) {
            // Convert once from the runtime's north-forward coordinates to the captured block coordinates.
            Quaternionf fromNormalized = new Quaternionf().rotationY(-sourceYaw(forward));
            var registered = new ArrayList<Arm>();
            for (CouplingEndpoint endpoint : definition.couplings()) {
                if (endpoint.kind() != CouplingEndpoint.Kind.RIGID) continue;
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
        BodyPose body = solution.bodies().get(snapshot.id());
        Quaternionf bodyRotation = orientation(body);
        Quaternionf inverseSourceBody = new Quaternionf(bodyRotation).rotateY(sourceYaw(forward)).conjugate();
        var next = new ArrayList<SnapshotBogeyAnimation.Frame>(arms.size());
        var nextParameters = new ArrayList<Parameters>(arms.size());
        for (Arm arm : arms) {
            CouplingEndpoint endpoint = arm.endpoint;
            var solved = solution.endpoints().get(endpoint.id());
            if (solved == null) continue;
            Quaternionf restingRotation = new Quaternionf(bodyRotation).rotateY(facingYaw(endpoint.facing()));
            var p = endpoint.pivot();
            var c = endpoint.effectiveComplete();
            Vector3f initialDirection = restingRotation.transform(new Vector3f(
                    (float) (c.x() - p.x()), (float) (c.y() - p.y()), (float) (c.z() - p.z())));
            var actualArm = solved.complete().subtract(solved.pivot());
            Vector3f actualDirection = new Vector3f((float) actualArm.x(), (float) actualArm.y(), (float) actualArm.z());
            Quaternionf swing = new Quaternionf();
            if (initialDirection.lengthSquared() > 1e-12f && actualDirection.lengthSquared() > 1e-12f)
                swing.rotationTo(initialDirection.normalize(), actualDirection.normalize());
            Quaternionf relative = new Quaternionf(inverseSourceBody).mul(swing).mul(restingRotation);
            var offset = solved.pivot().subtract(body.position());
            Vector3f local = inverseSourceBody.transform(new Vector3f((float) offset.x(), (float) offset.y(), (float) offset.z()));
            // The frame origin is the registered pivot. Rendering subtracts the arm model's own pivot.
            next.add(new SnapshotBogeyAnimation.Frame(arm.position, local.x + .5f, local.y + .5f, local.z + .5f,
                    relative.x, relative.y, relative.z, relative.w, 0, 0));
            UUID partner = runtime.couplingPartner(endpoint.id());
            var gap = partner == null ? null : solved.complete().subtract(solution.endpoints().get(partner).complete());
            nextParameters.add(new Parameters(endpoint.id(), arm.position, gap == null ? 0 : Math.sqrt(gap.dot(gap)),
                    CouplerAngles.from(endpoint, body, solved), endpoint.adapter() == null ? null
                    : new AdapterAttachment(endpoint.adapter(), modelPoint(endpoint.pivot()), modelPoint(endpoint.complete()),
                            !endpoint.model().equals(endpoint.adapter().firstModel()))));
        }
        poses.setFrames(next);
        setParameters(nextParameters);
    }

    private void setParameters(List<Parameters> parameters) {
        previousParameters = currentParameters = targetParameters = List.copyOf(parameters);
        parameterInterpolationSteps = 0;
    }

    /** Runtime hook parameters compensate the body floor; attachment geometry remains the registered mesh. */
    private Point modelPoint(Point point) {
        return new Point(point.x(), point.y() + floorOffset, point.z());
    }

    private static void writePoint(CompoundTag root, String key, Point point) {
        var tag = new CompoundTag();
        tag.putDouble("X", point.x()); tag.putDouble("Y", point.y()); tag.putDouble("Z", point.z());
        root.put(key, tag);
    }

    private static Point readPoint(CompoundTag root, String key) {
        var tag = root.getCompound(key);
        return new Point(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
    }

    private static Quaternionf orientation(BodyPose pose) {
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

    private static float facingYaw(CouplingEndpoint.Facing facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> (float) (-Math.PI / 2);
            case SOUTH -> (float) Math.PI;
            case WEST -> (float) (Math.PI / 2);
        };
    }

    private record Arm(CouplingEndpoint endpoint, BlockPos position) {}
}
