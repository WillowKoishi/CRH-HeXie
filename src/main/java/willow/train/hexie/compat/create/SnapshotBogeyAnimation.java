package willow.train.hexie.compat.create;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import willow.train.hexie.vehicle.assembly.CarriageSnapshot;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.BodyPose;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;

/** Compact, body-local animation for every support, independent of Create's two-bogey facade. */
public final class SnapshotBogeyAnimation {
    public record Frame(BlockPos position, float x, float y, float z,
                        float qx, float qy, float qz, float qw, double wheelAngle, float speed) {
        public Frame {
            position = position.immutable();
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || !Float.isFinite(qx) || !Float.isFinite(qy) || !Float.isFinite(qz)
                    || !Float.isFinite(qw) || !Double.isFinite(wheelAngle) || !Float.isFinite(speed))
                throw new IllegalArgumentException("Non-finite bogey animation");
        }

        public Quaternionf rotation() { return new Quaternionf(qx, qy, qz, qw); }

        public Frame interpolate(Frame next, float fraction) {
            if (fraction >= 1) return next;
            Quaternionf rotation = rotation().slerp(next.rotation(), fraction);
            return new Frame(position, lerp(x, next.x, fraction), lerp(y, next.y, fraction),
                    lerp(z, next.z, fraction), rotation.x, rotation.y, rotation.z, rotation.w,
                    wheelAngle + (next.wheelAngle - wheelAngle) * fraction,
                    lerp(speed, next.speed, fraction));
        }

        private static float lerp(float a, float b, float f) { return a + (b - a) * f; }
    }

    private List<Frame> current = List.of(), previous = List.of(), target = List.of();
    private VehicleRuntime sourceRuntime;
    private CarriageSnapshot sourceSnapshot;
    private final Map<BlockPos, BlockState> supportStates = new HashMap<>();
    private double[] previousDistances = new double[0];
    private int interpolationSteps;

    public List<Frame> frames() { return current; }

    /** Shares the compact pose interpolation/serialization with other independent moving parts. */
    public void setFrames(List<Frame> frames) {
        previous = current = target = List.copyOf(frames);
        interpolationSteps = 0;
    }

    public Frame frame(int index, float partialTick) {
        return previous.size() == current.size()
                ? previous.get(index).interpolate(current.get(index), partialTick) : current.get(index);
    }

    /** Called after the authoritative pose is applied. Repeated placement in one tick cannot spin wheels twice. */
    public void update(VehicleRuntime runtime, CarriageSnapshot snapshot, Direction forward, long gameTime) {
        update(runtime, snapshot, forward, gameTime, false);
    }

    public void update(VehicleRuntime runtime, CarriageSnapshot snapshot, Direction forward, long gameTime,
                       boolean derailed) {
        update(runtime, snapshot, forward, gameTime, derailed, forward);
    }

    public void update(VehicleRuntime runtime, CarriageSnapshot snapshot, Direction forward, long gameTime,
                       boolean derailed, Direction modelForward) {
        if (snapshot == null) return;
        var definition = runtime.definition(snapshot.id());
        var solution = runtime.placement();
        BodyPose body = solution.bodies().get(snapshot.id());
        Quaternionf sourceRotation = orientation(body).rotateY(sourceYaw(forward));
        Quaternionf inverseBody = new Quaternionf(sourceRotation).conjugate();
        boolean advance = sourceRuntime == runtime && previousDistances.length == definition.bogeys().size();
        if (sourceSnapshot != snapshot) {
            supportStates.clear();
            for (var block : snapshot.blocks())
                if (snapshot.supports().contains(block.position())) supportStates.put(block.position(), block.state());
            sourceSnapshot = snapshot;
        }
        if (previousDistances.length != definition.bogeys().size()) previousDistances = new double[definition.bogeys().size()];
        var next = new ArrayList<Frame>(definition.bogeys().size());
        for (int index = 0; index < definition.bogeys().size(); index++) {
            var definitionBogey = definition.bogeys().get(index);
            var position = snapshot.supports().get(index);
            var support = solution.bogeys().get(definitionBogey.id());
            var sample = support.track();
            var tangent = sample.tangent();
            double yaw = Math.atan2(-tangent.x(), -tangent.z());
            double pitch = Math.asin(Math.clamp(tangent.y(), -1, 1));
            BodyPose flatFrame = new BodyPose(sample.position(), yaw, pitch, 0);
            double roll = Math.atan2(-sample.normal().dot(flatFrame.rotate(new Point(1, 0, 0))),
                    sample.normal().dot(flatFrame.rotate(new Point(0, 1, 0))));
            BodyPose trackFrame = new BodyPose(sample.position(), yaw, pitch, roll);
            // The solver's support pivot can differ from the block centre. Preserve the registered pivot.
            Vector3f normalizedBlock = new Quaternionf().rotationY(sourceYaw(forward))
                    .transform(new Vector3f(position.getX(), position.getY(), position.getZ()));
            Point mount = definitionBogey.mount().bodyPoint();
            var pivotOffset = trackFrame.rotate(new Point(mount.x() - normalizedBlock.x,
                    mount.y() - normalizedBlock.y, mount.z() - normalizedBlock.z));
            var centre = support.pivot().subtract(pivotOffset).subtract(body.position());
            Vector3f local = inverseBody.transform(new Vector3f((float) centre.x(), (float) centre.y(), (float) centre.z()));
            // Preserve the actual placed model's axis, including asymmetric and backward variants.
            // Track direction is the animation reference, not permission to turn a placed model around.
            boolean xAxis = supportStates.get(position).getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                    .orElse(Direction.Axis.Z) == Direction.Axis.X;
            float modelYaw = sourceYaw(forward) + (xAxis ? (float) (Math.PI / 2) : 0);
            // QJ's existing station preview explicitly faces opposite the assembly scan direction.
            if (definitionBogey.modelId().equals("hexie:qj") || definitionBogey.modelId().equals("hexie:qj_backward")) {
                Direction preview = modelForward.getOpposite();
                if (preview == Direction.SOUTH || preview == Direction.EAST) modelYaw += (float) Math.PI;
            }
            Quaternionf bogeyRotation;
            if (derailed) {
                // Create's derailed bogeys use the body's horizontal heading and a per-support
                // skew, rather than continuing to steer along the rail. Derive the skew from
                // our persistent support id so reloads and different clients agree without
                // another synchronized field or a dependency on the two-bogey facade.
                int derailDegrees = Math.floorMod(definitionBogey.id().hashCode(), 60) - 30;
                if (derailDegrees == 0) derailDegrees = 1;
                bogeyRotation = new Quaternionf().rotationY((float) body.yaw())
                        .rotateY(modelYaw + (float) Math.toRadians(derailDegrees));
            } else bogeyRotation = orientation(trackFrame).rotateY(modelYaw);
            Quaternionf relative = new Quaternionf(inverseBody).mul(bogeyRotation);
            double speed = runtime.bogeySpeed(definitionBogey.id());
            double angle = index < current.size() && current.get(index).position().equals(position)
                    ? current.get(index).wheelAngle() : 0;
            double modelDirection = Math.cos(modelYaw) < 0 ? -1 : 1;
            if (advance) angle -= Math.toDegrees((support.distance() - previousDistances[index])
                    * modelDirection / definitionBogey.geometry().wheelRadius());
            previousDistances[index] = support.distance();
            next.add(new Frame(position, local.x + .5f, local.y + .5f, local.z + .5f,
                    relative.x, relative.y, relative.z, relative.w, angle, (float) (speed * modelDirection / 20)));
        }
        sourceRuntime = runtime;
        previous = current = target = List.copyOf(next);
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

    /** One packed float array per support; no repeated structure, model, block entity or body data. */
    public CompoundTag write() {
        var root = new CompoundTag();
        var list = new ListTag();
        for (Frame frame : current) {
            var tag = new CompoundTag();
            tag.putLong("Pos", frame.position().asLong());
            float[] values = {frame.x, frame.y, frame.z, frame.qx, frame.qy, frame.qz, frame.qw, frame.speed};
            int[] packed = new int[values.length];
            for (int i = 0; i < values.length; i++) packed[i] = Float.floatToIntBits(values[i]);
            tag.putIntArray("Pose", packed);
            tag.putDouble("Wheel", frame.wheelAngle);
            list.add(tag);
        }
        root.put("Supports", list);
        return root;
    }

    public void read(CompoundTag root, int steps) {
        var decoded = new ArrayList<Frame>();
        for (Tag value : root.getList("Supports", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) value;
            int[] packed = tag.getIntArray("Pose");
            if (packed.length != 8) continue;
            float[] f = new float[8];
            for (int i = 0; i < f.length; i++) f[i] = Float.intBitsToFloat(packed[i]);
            decoded.add(new Frame(BlockPos.of(tag.getLong("Pos")), f[0], f[1], f[2],
                    f[3], f[4], f[5], f[6], tag.getDouble("Wheel"), f[7]));
        }
        target = List.copyOf(decoded);
        boolean sameSupports = current.size() == target.size();
        for (int i = 0; sameSupports && i < current.size(); i++)
            sameSupports = current.get(i).position.equals(target.get(i).position);
        if (!sameSupports || steps <= 0) previous = current = target;
        interpolationSteps = Math.max(1, steps);
    }

    public void tick() {
        previous = current;
        if (interpolationSteps <= 0) return;
        if (interpolationSteps == 1) current = target;
        else {
            var next = new ArrayList<Frame>(current.size());
            for (int i = 0; i < current.size(); i++)
                next.add(current.get(i).interpolate(target.get(i), 1f / interpolationSteps));
            current = List.copyOf(next);
        }
        interpolationSteps--;
    }
}
