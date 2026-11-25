package willow.train.hexie.compat.create.motion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge.CouplingEndpointVisualView;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.HexieBogeyBlockEntityView;
import willow.train.hexie.core.api.cab.CabDisplayState;
import willow.train.hexie.core.api.cab.CabDisplayStateNbt;

// split from SnapshotCarriageEntity.java
public final class SnapshotCarriagePlayback {
    private final SnapshotCarriageEntity carriageEntity;

    public SnapshotCarriagePlayback(SnapshotCarriageEntity carriageEntity) { this.carriageEntity = carriageEntity; }

    Vec3 interpolationTarget;
    int interpolationSteps;
    CompoundTag pendingClientMotionFrame;

    record ClientAnimation(long contactTick, List<SnapshotBogeyAnimation.Frame> bogeys,
                                   SnapshotCouplerAnimation.State couplers) {
        ClientAnimation interpolate(ClientAnimation next, float fraction) {
            return new ClientAnimation(next.contactTick,
                    SnapshotBogeyAnimation.interpolate(bogeys, next.bogeys, fraction),
                    couplers.interpolate(next.couplers, fraction));
        }
    }
    final CarriagePoseInterpolation<ClientAnimation> poseInterpolation = new CarriagePoseInterpolation<>();
    // A consist has one playback time, even when its carriage spawn packets arrive on different ticks.
    // Level keys keep a new world session independent from the previous world's train identities.
    private static final Map<Level, Map<UUID, CarriagePoseInterpolation.Timeline>> CLIENT_POSE_TIMELINES =
            new java.util.WeakHashMap<>();
    private UUID clientPoseGroup;
    private CarriagePoseInterpolation.Timeline clientPoseTimeline;

    public boolean isControlledByLocalInstance() {
        // Create suppresses vanilla entity movement packets; this carrier interpolates them.
        return !carriageEntity.level().isClientSide;
    }

    public Vec3 getPrevPositionVec() {
        return carriageEntity.previousPositionInvalid() ? carriageEntity.position() : new Vec3(carriageEntity.xo, carriageEntity.yo, carriageEntity.zo);
    }

    public void applyClientMotionFrame(CompoundTag frame) {
        carriageEntity.motionFrameTick = frame.getLong("PoseTick");
        readBogeySettings(frame.getList("BogeySettings", Tag.TAG_COMPOUND));
            carriageEntity.cabDisplay.cabDisplayState = frame.contains("CabDisplay", net.minecraft.nbt.Tag.TAG_COMPOUND)
                    ? CabDisplayStateNbt.read(frame.getCompound("CabDisplay"))
                    : CabDisplayState.speedOnly(frame.getDouble("DisplaySpeedKmh"));
            var controls = frame.getCompound(frame.contains("CabControls") ? "CabControls" : "TestCab");
            carriageEntity.cabDisplay.cabControlStates = HexieBridges.cabControl().cabModule().readFrames(controls);
            carriageEntity.readCabDisplayFrames(controls);
            carriageEntity.cabDisplay.simpleCabDisplayState = frame.contains("SimpleCabDisplay", Tag.TAG_COMPOUND)
                    ? CabDisplayStateNbt.read(frame.getCompound("SimpleCabDisplay")) : carriageEntity.cabDisplay.cabDisplayState;
            carriageEntity.setCoordinateBasis(Direction.byName(frame.getString("Forward")));
    }

    public boolean queueClientPose(CompoundTag frame) {
        bindClientPoseTimeline();
        var forward = Direction.byName(frame.getString("Forward"));
        var rotation = motionFrameRotation(frame.getFloat("Yaw"), frame.getFloat("Pitch"), frame.getFloat("Roll"), forward);
        long tick = frame.contains("SampleTick") ? frame.getLong("SampleTick") : frame.getLong("PoseTick");
        var animation = new ClientAnimation(frame.getLong("PoseTick"),
                SnapshotBogeyAnimation.decode(frame.getCompound("Bogeys")),
                SnapshotCouplerAnimationNbt.decode(frame.getCompound("Couplers")));
        return poseInterpolation.accept(new Vec3(frame.getDouble("X"), frame.getDouble("Y"), frame.getDouble("Z")),
                new Vec3(frame.getDouble("Vx"), frame.getDouble("Vy"), frame.getDouble("Vz")), rotation, tick, animation);
    }

    public void bindClientPoseTimeline() {
        UUID group = carriageEntity.getEntityData().get(SnapshotCarriageEntity.COMPATIBILITY_TRAIN).orElse(carriageEntity.trainId == null ? carriageEntity.getUUID() : carriageEntity.trainId);
        if (group.equals(clientPoseGroup)) return;
        releaseClientPoseTimeline();
        var timelines = CLIENT_POSE_TIMELINES.computeIfAbsent(carriageEntity.level(), ignored -> new HashMap<>());
        clientPoseGroup = group;
        clientPoseTimeline = timelines.computeIfAbsent(group, ignored -> new CarriagePoseInterpolation.Timeline());
        poseInterpolation.attach(clientPoseTimeline);
    }

    public void releaseClientPoseTimeline() {
        if (clientPoseTimeline == null) return;
        poseInterpolation.detach();
        var timelines = CLIENT_POSE_TIMELINES.get(carriageEntity.level());
        if (timelines != null && clientPoseTimeline.isEmpty()) {
            timelines.remove(clientPoseGroup, clientPoseTimeline);
            if (timelines.isEmpty()) CLIENT_POSE_TIMELINES.remove(carriageEntity.level());
        }
        clientPoseGroup = null;
        clientPoseTimeline = null;
    }

    public static org.joml.Quaternionf motionFrameRotation(float yaw, float pitch, float roll, Direction forward) {
        return new org.joml.Quaternionf().rotationY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(pitch)).rotateZ((float) Math.toRadians(roll))
                .rotateY((float) SnapshotCarriageEntity.sourceYaw(forward));
    }

    public ClientAnimation animationAt(float partial) {
        var window = poseInterpolation.window(partial);
        return window.from().payload().interpolate(window.to().payload(), window.fraction());
    }

    public void applyClientPose() {
        carriageEntity.setPos(poseInterpolation.position(1));
        var angles = poseInterpolation.rotation(1).rotateY((float) -carriageEntity.sourceYaw()).getEulerAnglesYXZ(new org.joml.Vector3f());
        carriageEntity.yaw = (float) Math.toDegrees(angles.y);
        carriageEntity.pitch = (float) Math.toDegrees(angles.x);
        carriageEntity.roll = (float) Math.toDegrees(angles.z);
        var beforeBogeys = carriageEntity.bogeyAnimation.frames();
        var beforeCouplers = carriageEntity.couplerAnimation.frames();
        var visuals = carriageEntity.couplerAnimation.parameters().stream().map(SnapshotCouplerAnimation.Parameters::visual).toList();
        double oldFloor = carriageEntity.couplerAnimation.floorOffset();
        var previous = animationAt(0);
        var current = animationAt(1);
        carriageEntity.bogeyAnimation.playback(previous.bogeys, current.bogeys);
        carriageEntity.couplerAnimation.playback(previous.couplers, current.couplers);
        updateBogeyStructure(beforeBogeys);
        updateCouplerStructure(beforeCouplers, visuals, oldFloor);
    }

    public void onClientRemoval() {
        releaseClientPoseTimeline();
        if (carriageEntity.sync.compatibilityCarriage != null) {
            var dimensional = carriageEntity.sync.compatibilityCarriage.getDimensionalIfPresent(carriageEntity.level().dimension());
            if (dimensional != null && dimensional.entity.get() == carriageEntity) dimensional.entity.clear();
        }
        if (carriageEntity.sounds != null) carriageEntity.sounds.stop();
        carriageEntity.sync.compatibilityCarriage = null;
    }

    public void readBogeySettings(ListTag settings) {
        if (carriageEntity.getContraption() == null) return;
        boolean changed = false;
        for (int i = 0; i < settings.size(); i++) {
            var entry = settings.getCompound(i);
            var pos = BlockPos.of(entry.getLong("Pos"));
            var support = HexieBridges.vehicle().assemblyBogeySupport().resolve(carriageEntity.getContraption().getBlocks(), pos);
            if (support == null) continue;
            var info = carriageEntity.getContraption().getBlocks().get(support.owner());
            int height = entry.getInt("Height"), offset = entry.getInt("Longitudinal");
            if (!HexieBridges.worldBlock().bogeyFloorData().valid(height)
                    || !HexieBridges.worldBlock().bogeyFloorData().validLongitudinal(offset)) continue;
            if (height == HexieBridges.worldBlock().bogeyFloorData().value(info.nbt())
                    && offset == HexieBridges.worldBlock().bogeyFloorData().longitudinalFromEntity(info.nbt())
                    && entry.getBoolean("Reversed") == HexieBridges.worldBlock().bogeyFloorData().reversedFromEntity(info.nbt())) continue;
            var data = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
            HexieBridges.worldBlock().bogeyFloorData().writeReversed(data, entry.getBoolean("Reversed"));
            HexieBridges.worldBlock().bogeyFloorData().write(data, height);
            HexieBridges.worldBlock().bogeyFloorData().writeLongitudinal(data, offset);
            carriageEntity.setBlock(support.owner(), new StructureBlockInfo(support.owner(), info.state(), data));
            // Invalidating the mesh does not reload ClientContraption's virtual BEs.
            // The top model reads its offset from that live BE, not the saved block map.
            var rendered = carriageEntity.getContraption().getBlockEntityClientSide(support.owner());
            if (rendered instanceof HexieBogeyBlockEntityView bogey) {
                ((AbstractBogeyBlockEntity) rendered).setBogeyData(data.getCompound("BogeyData").copy());
                bogey.markCarriageView();
            }
            if (rendered instanceof CRH2HeadEntityView head) {
                head.updateBogey(true, data.getCompound("BogeyData"));
                head.markCarriageView();
            }
            changed = true;
        }
        if (changed) {
            carriageEntity.getContraption().invalidateClientContraptionStructure();
            carriageEntity.getContraption().invalidateClientContraptionChildren();
        }
    }

    public void readBogeyAnimation(CompoundTag tag, int steps) {
        var before = carriageEntity.bogeyAnimation.frames();
        carriageEntity.bogeyAnimation.read(tag, steps);
        updateBogeyStructure(before);
    }

    public void updateBogeyStructure(List<SnapshotBogeyAnimation.Frame> before) {
        var after = carriageEntity.bogeyAnimation.frames();
        boolean supportsChanged = before.size() != after.size();
        for (int i = 0; !supportsChanged && i < before.size(); i++)
            supportsChanged = !before.get(i).position().equals(after.get(i).position());
        if (supportsChanged && carriageEntity.getContraption() != null) carriageEntity.getContraption().invalidateClientContraptionChildren();
    }

    public void readCouplerAnimation(CompoundTag tag, int steps) {
        var before = carriageEntity.couplerAnimation.frames();
        var previousVisuals = carriageEntity.couplerAnimation.parameters().stream().map(SnapshotCouplerAnimation.Parameters::visual).toList();
        double oldFloor = carriageEntity.couplerAnimation.floorOffset();
        carriageEntity.couplerAnimation.read(tag, steps);
        updateCouplerStructure(before, previousVisuals, oldFloor);
    }

    public void updateCouplerStructure(List<SnapshotBogeyAnimation.Frame> before,
            List<CouplingEndpointVisualView> previousVisuals, double oldFloor) {
        var after = carriageEntity.couplerAnimation.frames();
        boolean visualsChanged = !previousVisuals.equals(carriageEntity.couplerAnimation.parameters().stream()
                .map(SnapshotCouplerAnimation.Parameters::visual).toList());
        boolean hooksChanged = before.size() != after.size();
        for (int i = 0; !hooksChanged && i < before.size(); i++)
            hooksChanged = !before.get(i).position().equals(after.get(i).position());
        if (carriageEntity.getContraption() != null) {
            boolean floorChanged = oldFloor != carriageEntity.couplerAnimation.floorOffset();
            if (hooksChanged) carriageEntity.floorBoundsSource = null;
            if (visualsChanged || floorChanged || hooksChanged && (oldFloor != 0 || carriageEntity.couplerAnimation.floorOffset() != 0))
                carriageEntity.getContraption().invalidateClientContraptionStructure();
            else if (hooksChanged) carriageEntity.getContraption().invalidateClientContraptionChildren();
            if (floorChanged) carriageEntity.getContraption().invalidateColliders();
            if (floorChanged || hooksChanged) carriageEntity.updateRotatedBounds();
        }
    }

    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // Vanilla position packets must not restart the independently received pose clock.
        if (!poseInterpolation.initialized()) {
            interpolationTarget = new Vec3(x, y, z);
            interpolationSteps = Math.max(1, steps);
        }
    }
}
