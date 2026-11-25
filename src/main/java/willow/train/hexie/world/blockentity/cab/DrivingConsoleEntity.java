package willow.train.hexie.world.blockentity.cab;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabControlBridge;
import willow.train.hexie.bridge.vehicle.CabModuleView;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.cab.CabControlsHost;
import willow.train.hexie.core.api.cab.CabDisplayHost;
import willow.train.hexie.core.api.cab.CabDisplaySource;
import willow.train.hexie.core.api.cab.CabDisplayState;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.CabSurfaceInstance;
import willow.train.hexie.core.api.cab.CabTimetableHost;
import willow.train.hexie.core.api.cab.DmiState;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.ControlState;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.world.blocks.emu.TestDrivingConsoleBlock;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

/** Live display sources and stationary handle positions. The vehicle owns a moving console's controls. */
public final class DrivingConsoleEntity extends BlockEntity implements CabDisplayHost, CabControlsHost,
        CabTimetableHost, CabSurfaceInstance, ControlSurface.AnimationSource, BlockEntityViews.DrivingConsoleEntityView {
    private CompoundTag pendingTimetable = new CompoundTag();
    @Override public CompoundTag timetable() { return pendingTimetable.copy(); }
    /** Opaque backend data; the console does not interpret Create's schedule format. */
    @Override public void readTimetable(CompoundTag data) {
        pendingTimetable = data.copy();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    private CabDisplaySource source = () -> CabDisplayState.speedOnly(0);
    private CabModuleView benchControls = HexieBridges.cabControl().createCabModule();
    private Supplier<CabState> controlsSource = () -> benchControls.state();
    private boolean vehicleSource;
    private record Motion(double start, double target, double time) {
        double at(double now) {
            return start + Math.copySign(Math.min(now - time, Math.abs(target - start)), target - start);
        }
    }
    private final Map<String, DrivingConsoleEntity.Motion> animations = new HashMap<>();

    /** Animation progress belongs to this desk, including its virtual block entity on a carriage. */
    // Carriage visuals and the player's hit test may sample the same moving handle on different threads.
    @Override public synchronized double visualTravel(BlockControl control, double target, double now) {
        var motion = animations.get(control.id());
        double current = motion == null || now < motion.time() ? target : motion.at(now);
        if (motion == null || motion.target() != target || now < motion.time())
            animations.put(control.id(), new DrivingConsoleEntity.Motion(current, target, now));
        return current;
    }
    public boolean hasVehicleSource() { return vehicleSource; }
    @Override public void controlsSource(Supplier<CabState> source) {
        controlsSource = Objects.requireNonNull(source); vehicleSource = true;
    }
    @Override public CabState controls() { return controlsSource.get(); }
    @Override public ControlState surfaceState() {
        var block = getBlockState();
        return block.getBlock() instanceof CabControlHost host ? host.surfaceState(block, controls()) : ControlState.EMPTY;
    }
    @Override public void operateControl(Player player, BlockControl control, int value) {
        operateControl(control.id(), value);
    }
    public void operateControl(String controlId, int value) {
        var block = getBlockState();
        if (!(block.getBlock() instanceof CabControlHost host)) return;
        var next = HexieBridges.cabControl().cabModule().operateControls(host, block, controlId, value, benchControls.state(), 0);
        if (next == null) return;
        benchControls.submit(next);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public void benchKeyboard(boolean toggleSimple, int mask) {
        benchKeyboard(toggleSimple, mask, ignored -> {});
    }
    public void benchKeyboard(boolean toggleSimple, int mask, Consumer<CabControlBridge.SimpleCabControlsShuntingReadinessView> onBlocked) {
        benchKeyboard(null, toggleSimple, mask, onBlocked);
    }
    public void benchKeyboard(Player driver, boolean toggleSimple, int mask) {
        benchKeyboard(driver, toggleSimple, mask, blocked -> HexieBridges.cabControl().simpleCabControls().notifyShuntingBlocked(driver, blocked));
    }
    private void benchKeyboard(@Nullable Player driver, boolean toggleSimple, int mask,
                               Consumer<CabControlBridge.SimpleCabControlsShuntingReadinessView> onBlocked) {
        var block = getBlockState();
        if (!(block.getBlock() instanceof CabControlHost host)) return;
        var next = HexieBridges.cabControl().simpleCabControls().applyBindings(host, block, benchControls.state(), toggleSimple, mask, 0, onBlocked);
        if (next == null) return;
        benchControls.keyboard(mask);
        HexieBridges.cabControl().simpleCabControls().submitAndNotify(driver, benchControls, next);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!pendingTimetable.isEmpty()) tag.put(PENDING_TIMETABLE, pendingTimetable.copy());
        tag.put("CabControls", benchControls.write());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pendingTimetable = tag.getCompound(PENDING_TIMETABLE).copy();
        benchControls = HexieBridges.cabControl().cabModule().read(tag.getCompound(tag.contains("CabControls") ? "CabControls" : "TestControls"));
        if (getBlockState().getBlock() instanceof CabControlHost host)
            benchControls.submit(HexieBridges.cabControl().cabModule().normalize(host.trainControl(getBlockState()), benchControls.state()));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public DrivingConsoleEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesInit.DRIVING_CONSOLE.get(), pos, state);
        if (state.getBlock() instanceof CabControlHost host) benchControls = (CabModuleView) host.createCabModule();
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
                            HexieBridges.cabControl().stationStopControlState().running())
                    : benchControls.update(benchControls.mode(), 1, 0, null, 0);
            var converter = desk.trainControl(block);
            command = benchControls.convertOutput(converter, command, 1, 0);
            double traction = state.enabled() ? converter.tractionOutput(command.throttle(), command.brake()) : 0;
            return new CabDisplayState(0, Map.of(), new DmiState(
                    state.automatic() ? DmiState.Mode.AUTOMATIC : DmiState.Mode.MANUAL,
                    null, CabControlBridge.TrainControlOperations.LINE_SPEED_LIMIT_KMH, 0,
                    Math.abs(traction), command.brake(), "静态测试台", "", "", "", ""));
        }
        return source.displayState();
    }

    public double speedKmh() {
        return displayState().speedKmh();
    }
}
