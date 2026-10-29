package willow.train.hexie.compat.create;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.BodyPose;
import willow.train.hexie.vehicle.runtime.CarriageDefinition;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Independent rigid coupler arms, driven by the exact endpoint poses used by the constraint solver. */
public final class SnapshotCouplerAnimation {
    private final SnapshotBogeyAnimation poses = new SnapshotBogeyAnimation();
    private CarriageDefinition sourceDefinition;
    private Direction sourceForward;
    private List<Arm> arms = List.of();

    public List<SnapshotBogeyAnimation.Frame> frames() { return poses.frames(); }
    public SnapshotBogeyAnimation.Frame frame(int index, float partialTick) { return poses.frame(index, partialTick); }
    public CompoundTag write() { return poses.write(); }
    public void read(CompoundTag tag, int steps) { poses.read(tag, steps); }
    public void tick() { poses.tick(); }

    public void update(VehicleRuntime runtime, CarriageSnapshot snapshot, Direction forward) {
        if (snapshot == null) return;
        CarriageDefinition definition = runtime.definition(snapshot.id());
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
            return;
        }
        var solution = runtime.placement();
        BodyPose body = solution.bodies().get(snapshot.id());
        Quaternionf bodyRotation = orientation(body);
        Quaternionf inverseSourceBody = new Quaternionf(bodyRotation).rotateY(sourceYaw(forward)).conjugate();
        var next = new ArrayList<SnapshotBogeyAnimation.Frame>(arms.size());
        for (Arm arm : arms) {
            CouplingEndpoint endpoint = arm.endpoint;
            var solved = solution.endpoints().get(endpoint.id());
            if (solved == null) continue;
            Quaternionf restingRotation = new Quaternionf(bodyRotation).rotateY(facingYaw(endpoint.facing()));
            var p = endpoint.pivot();
            var c = endpoint.complete();
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
        }
        poses.setFrames(next);
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
