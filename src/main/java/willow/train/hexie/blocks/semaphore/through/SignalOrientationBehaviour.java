package willow.train.hexie.blocks.semaphore.through;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

public class SignalOrientationBehaviour extends BlockEntityBehaviour implements ValueSettingsBehaviour {
    public static final BehaviourType<SignalOrientationBehaviour> TYPE = new BehaviourType<>();
    private final AdjustableSignalBlockEntity signal;
    private final ValueBoxTransform slot = new OrientationValueBox();
    private boolean trackAligned = true;
    private int quarterTurns;
    private int fineAngle;
    private float trackAngle;
    private boolean hasTrackAngle;

    public SignalOrientationBehaviour(AdjustableSignalBlockEntity signal) {
        super(signal);
        this.signal = signal;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public int netId() {
        return 1;
    }

    @Override
    public boolean isSafeNBT() {
        return true;
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        CompoundTag settings = new CompoundTag();
        settings.putBoolean("TrackAligned", trackAligned);
        settings.putInt("QuarterTurns", quarterTurns);
        settings.putInt("FineAngle", fineAngle);
        settings.putFloat("TrackAngle", trackAngle);
        settings.putBoolean("HasTrackAngle", hasTrackAngle);
        tag.put("SignalOrientation", settings);
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (!tag.contains("SignalOrientation"))
            return;
        CompoundTag settings = tag.getCompound("SignalOrientation");
        trackAligned = settings.getBoolean("TrackAligned");
        quarterTurns = Math.floorMod(settings.getInt("QuarterTurns"), 4);
        fineAngle = Mth.clamp(settings.getInt("FineAngle"), -45, 45);
        trackAngle = settings.getFloat("TrackAngle");
        hasTrackAngle = settings.getBoolean("HasTrackAngle") && Float.isFinite(trackAngle);
        if (!hasTrackAngle)
            trackAngle = 0;
    }

    @Override
    public void lazyTick() {
        if (getWorld() == null || getWorld().isClientSide)
            return;
        BlockPos target = signal.edgePoint.getGlobalPosition();
        if (!getWorld().hasChunkAt(target))
            return;
        BlockState state = getWorld().getBlockState(target);
        if (!(state.getBlock() instanceof ITrackBlock track))
            return;
        Vec3 tangent;
        var curve = signal.edgePoint.getTargetBezier();
        if (curve != null) {
            if (!(getWorld().getBlockEntity(target) instanceof TrackBlockEntity trackEntity))
                return;
            BezierConnection connection = trackEntity.getConnections().get(curve.curveTarget());
            if (connection == null)
                return;
            double segments = Math.floor(connection.getLength() * 2);
            if (segments < 1)
                return;
            double before = Mth.clamp(curve.segment() / segments, 0, 1);
            double after = Mth.clamp((curve.segment() + 2.0) / segments, 0, 1);
            tangent = connection.getPosition(after).subtract(connection.getPosition(before));
        } else {
            List<Vec3> axes = track.getTrackAxes(getWorld(), target, state);
            if (axes.size() != 1)
                return;
            tangent = axes.getFirst();
        }
        tangent = tangent.scale(signal.edgePoint.getTargetDirection().getStep());
        if (tangent.horizontalDistanceSqr() < 1.0e-8)
            return;
        float angle = (float) Math.toDegrees(Math.atan2(-tangent.x, -tangent.z));
        if (!hasTrackAngle || Math.abs(Mth.wrapDegrees(angle - trackAngle)) > 0.01f) {
            trackAngle = angle;
            hasTrackAngle = true;
            signal.notifyUpdate();
        }
    }

    public boolean isTrackAligned() {
        return trackAligned;
    }

    public void setTrackAligned(boolean aligned) {
        if (getWorld() == null || getWorld().isClientSide || trackAligned == aligned)
            return;
        trackAligned = aligned;
        lazyTick();
        signal.notifyUpdate();
        playFeedbackSound(this);
    }

    public float getModelAngle() {
        float base = AngleHelper.horizontalAngle(signal.getBlockState().getValue(ThroughSignalBlock.FACING)) + 180;
        if (trackAligned && hasTrackAngle)
            base = trackAngle - quarterTurns * 90;
        return Mth.wrapDegrees(base + fineAngle);
    }

    public void rotateQuarterTurn() {
        if (getWorld() == null || getWorld().isClientSide)
            return;
        quarterTurns = (quarterTurns + 1) % 4;
        BlockState state = signal.getBlockState();
        getWorld().setBlock(getPos(), state.setValue(ThroughSignalBlock.FACING,
                state.getValue(ThroughSignalBlock.FACING).getClockWise()), Block.UPDATE_CLIENTS);
        signal.notifyUpdate();
        playFeedbackSound(this);
    }

    @Override
    public boolean mayInteract(Player player) {
        return player.mayBuild() && !player.isSpectator()
                && (player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH)
                || player.getOffhandItem().is(Tags.Items.TOOLS_WRENCH));
    }

    @Override
    public boolean onlyVisibleWithWrench() {
        return true;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public boolean testHit(Vec3 hit) {
        Vec3 local = hit.subtract(Vec3.atLowerCornerOf(getPos()));
        return local.x >= -0.001 && local.x <= 1.001 && local.y >= -0.001 && local.y <= 1.001
                && local.z >= -0.001 && local.z <= 1.001;
    }

    @Override
    public ValueBoxTransform getSlotPositioning() {
        return slot;
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
        return new ValueSettingsBoard(Component.translatable("gui.hexie.semaphore.fine_angle"), 90, 15,
                List.of(Component.translatable("gui.hexie.semaphore.angle")),
                new ValueSettingsFormatter(settings -> Component.literal((settings.value() - 45) + "°")));
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, fineAngle + 45);
    }

    @Override
    public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
        if (!mayInteract(player) || settings.row() != 0 || getWorld().isClientSide)
            return;
        int angle = Mth.clamp(settings.value(), 0, 90) - 45;
        if (fineAngle == angle)
            return;
        fineAngle = angle;
        signal.notifyUpdate();
        playFeedbackSound(this);
    }

    @Override
    public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hit) {
        if (mayInteract(player) && player.getItemInHand(hand).is(Tags.Items.TOOLS_WRENCH))
            rotateQuarterTurn();
    }

    @Override
    public String getClipboardKey() {
        return "SignalOrientation";
    }

    private static class OrientationValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 15);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return true;
        }

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            return true;
        }
    }
}
