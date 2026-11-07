package willow.train.hexie.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.cab.CabControlHost;
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.api.cab.CabSurfaceInstance;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.api.cab.CabDisplaySource;
import willow.train.hexie.api.cab.CabDisplayHost;
import willow.train.hexie.api.cab.CabDisplayState;
import willow.train.hexie.blocks.emu.TestDrivingConsoleBlock;
import willow.train.hexie.init.BlockEntitiesInit;
import willow.train.hexie.vehicle.control.CabModule;
import willow.train.hexie.vehicle.control.SimpleCabControls;
import willow.train.hexie.vehicle.control.SimpleCabControls.ShuntingReadiness;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/** Live display sources and stationary handle positions. The vehicle owns a moving console's controls. */
public final class DrivingConsoleEntity extends BlockEntity implements CabDisplayHost, willow.train.hexie.api.cab.CabControlsHost,
        willow.train.hexie.api.cab.CabTimetableHost, CabSurfaceInstance, ControlSurface.AnimationSource {
    private net.minecraft.nbt.CompoundTag pendingTimetable = new net.minecraft.nbt.CompoundTag();
    @Override public net.minecraft.nbt.CompoundTag timetable() { return pendingTimetable.copy(); }
    /** Opaque backend data; the console does not interpret Create's schedule format. */
    @Override public void readTimetable(net.minecraft.nbt.CompoundTag data) {
        pendingTimetable = data.copy();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    private CabDisplaySource source = () -> CabDisplayState.speedOnly(0);
    private willow.train.hexie.vehicle.control.CabModule benchControls = new willow.train.hexie.vehicle.control.CabModule();
    private java.util.function.Supplier<willow.train.hexie.vehicle.control.CabModule.State> controlsSource = () -> benchControls.state();
    private boolean vehicleSource;
    private record Motion(double start, double target, double time) {
        double at(double now) {
            return start + Math.copySign(Math.min(now - time, Math.abs(target - start)), target - start);
        }
    }
    private final Map<String, Motion> animations = new HashMap<>();

    /** Animation progress belongs to this desk, including its virtual block entity on a carriage. */
    // Carriage visuals and the player's hit test may sample the same moving handle on different threads.
    @Override public synchronized double visualTravel(BlockControl control, double target, double now) {
        var motion = animations.get(control.id());
        double current = motion == null || now < motion.time() ? target : motion.at(now);
        if (motion == null || motion.target() != target || now < motion.time())
            animations.put(control.id(), new Motion(current, target, now));
        return current;
    }
    public boolean hasVehicleSource() { return vehicleSource; }
    @Override public void controlsSource(java.util.function.Supplier<willow.train.hexie.vehicle.control.CabModule.State> source) {
        controlsSource = Objects.requireNonNull(source); vehicleSource = true;
    }
    @Override public willow.train.hexie.vehicle.control.CabModule.State controls() { return controlsSource.get(); }
    @Override public ControlState surfaceState() {
        var block = getBlockState();
        return block.getBlock() instanceof CabControlHost host ? host.surfaceState(block, controls()) : ControlState.EMPTY;
    }
    @Override public void operateControl(Player player, BlockControl control, int value) {
        operateControl(control.id(), value);
    }
    public void operateControl(String controlId, int value) {
        var block = getBlockState();
        if (!(block.getBlock() instanceof willow.train.hexie.api.cab.CabControlHost host)) return;
        var next = CabModule.operateControls(host, block, controlId, value, benchControls.state(), 0);
        if (next == null) return;
        benchControls.submit(next);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public void benchKeyboard(boolean toggleSimple, int mask) {
        benchKeyboard(toggleSimple, mask, ignored -> {});
    }
    public void benchKeyboard(boolean toggleSimple, int mask, Consumer<ShuntingReadiness> onBlocked) {
        benchKeyboard(null, toggleSimple, mask, onBlocked);
    }
    public void benchKeyboard(Player driver, boolean toggleSimple, int mask) {
        benchKeyboard(driver, toggleSimple, mask, blocked -> SimpleCabControls.notifyShuntingBlocked(driver, blocked));
    }
    private void benchKeyboard(@Nullable Player driver, boolean toggleSimple, int mask,
                               Consumer<ShuntingReadiness> onBlocked) {
        var block = getBlockState();
        if (!(block.getBlock() instanceof CabControlHost host)) return;
        var next = SimpleCabControls.applyBindings(host, block, benchControls.state(), toggleSimple, mask, 0, onBlocked);
        if (next == null) return;
        benchControls.keyboard(mask);
        SimpleCabControls.submitAndNotify(driver, benchControls, next);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!pendingTimetable.isEmpty()) tag.put(PENDING_TIMETABLE, pendingTimetable.copy());
        tag.put("CabControls", benchControls.write());
    }
    @Override protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pendingTimetable = tag.getCompound(PENDING_TIMETABLE).copy();
        benchControls = willow.train.hexie.vehicle.control.CabModule.read(tag.getCompound(tag.contains("CabControls") ? "CabControls" : "TestControls"));
    }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public DrivingConsoleEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesInit.DRIVING_CONSOLE.get(), pos, state);
    }

    /** Replaces the previous source, including any legacy speed-only binding. */
    @Override
    public void displaySource(CabDisplaySource source) {
        this.source = Objects.requireNonNull(source);
    }

    /** Legacy speed-only adapter. Like displaySource, the last binding wins. */
    public void speedSource(DoubleSupplier source) {
        Objects.requireNonNull(source);
        displaySource(() -> CabDisplayState.speedOnly(source.getAsDouble()));
    }

    @Override
    public CabDisplayState displayState() {
        var block = getBlockState();
        if (!vehicleSource && block.getBlock() instanceof TestDrivingConsoleBlock desk) {
            var state = benchControls.state();
            var command = state.simple()
                    ? benchControls.updateSimple(false, 1, 0, null, 0,
                            willow.train.hexie.vehicle.control.StationStopControl.State.RUNNING)
                    : benchControls.update(benchControls.mode(), 1, 0, null, 0);
            var converter = desk.trainControl(block);
            command = benchControls.convertOutput(converter, command, 1, 0);
            double traction = state.enabled() ? converter.tractionOutput(command.throttle(), command.brake()) : 0;
            return new CabDisplayState(0, java.util.Map.of(), new willow.train.hexie.api.cab.DmiState(
                    state.automatic() ? willow.train.hexie.api.cab.DmiState.Mode.AUTOMATIC : willow.train.hexie.api.cab.DmiState.Mode.MANUAL,
                    null, willow.train.hexie.vehicle.control.TrainControl.LINE_SPEED_LIMIT_KMH, 0,
                    Math.abs(traction), command.brake(), "静态测试台", "", "", "", ""));
        }
        return source.displayState();
    }

    public double speedKmh() {
        return displayState().speedKmh();
    }
}
