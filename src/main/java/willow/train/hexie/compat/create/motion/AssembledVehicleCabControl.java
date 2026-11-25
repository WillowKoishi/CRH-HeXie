package willow.train.hexie.compat.create.motion;

import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabControlBridge.AutomaticTrainControlTargetView;
import willow.train.hexie.bridge.vehicle.CabControlBridge.CabControlArbitrationCommandView;
import willow.train.hexie.bridge.vehicle.CabControlBridge.CabControlArbitrationSelectionView;
import willow.train.hexie.bridge.vehicle.CabControlBridge.StationStopControlStateView;
import willow.train.hexie.bridge.vehicle.CabControlBridge.TrainControlSource;
import willow.train.hexie.bridge.vehicle.CabModuleView;
import willow.train.hexie.compat.create.signal.CreateCabTimetable;
import willow.train.hexie.compat.create.signal.CreateNavigationState;
import willow.train.hexie.compat.create.signal.CreateSharedCabTimetables;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.CabState.Input;
import willow.train.hexie.core.api.cab.CabState.Mode;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.core.api.cab.TrainControlLimits;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;
import willow.train.hexie.core.api.traincontrol.CabTrainControl.Demand;
import willow.train.hexie.core.api.traincontrol.TrainCommand;

// split from AssembledVehicleMotion.java
public final class AssembledVehicleCabControl {
    private final AssembledVehicleMotion vehicleMotion;

    public AssembledVehicleCabControl(AssembledVehicleMotion vehicleMotion) { this.vehicleMotion = vehicleMotion; }

    public record ControlKey(UUID carriage, BlockPos position) {
        public ControlKey { position = position.immutable(); }
    }
    public final Map<ControlKey, Controller> controllers = new HashMap<>();

    public final Set<UUID> controlledCarriages = new HashSet<>();

    public boolean isManuallyControlled(UUID carriage) {
        var selection = cabSelection(carriage);
        if (selection.hasEnabledCab()) return selection.conflicting() || selection.mode() == Mode.MANUAL;
        return hasNativeManualControl(carriage);
    }

    public boolean hasNativeManualControl(UUID carriage) {
        return controllers.entrySet().stream().anyMatch(entry -> vehicleMotion.runtime.connected(carriage, entry.getKey().carriage())
                && entry.getValue().cabControls == null && entry.getValue().attended && !entry.getValue().timedOut);
    }

    public CabControlArbitrationSelectionView cabSelection(UUID member) {
        return HexieBridges.cabControl().cabControlArbitration().select(orderedCabs(member).stream()
                .map(e -> e.getValue().cabControls.state()).toList());
    }

    public List<Map.Entry<ControlKey, Controller>> orderedCabs(UUID member) {
        return controllers.entrySet().stream()
                .filter(e -> e.getValue().cabControls != null && vehicleMotion.runtime.connected(member, e.getKey().carriage()))
                .sorted(java.util.Comparator.<Map.Entry<ControlKey, Controller>>comparingLong(e -> e.getValue().inputSequence)
                        .thenComparing(e -> e.getKey().carriage()).thenComparingLong(e -> e.getKey().position().asLong()))
                .toList();
    }

    public boolean hasEnabledCabModule(UUID member) { return cabSelection(member).hasEnabledCab(); }

    public CreateCabTimetable cabTimetable(ControlKey key) {
        var driver = controllers.get(key);
        return driver == null ? null : driver.cabTimetable;
    }

    public CabState cabState(ControlKey key) {
        var driver = controllers.get(key);
        return driver == null || driver.cabControls == null ? null : driver.cabControls.state();
    }

    public Map<ControlKey, CabState> cabStates(UUID carriage) {
        var result = new LinkedHashMap<ControlKey, CabState>();
        controllers.forEach((key, driver) -> {
            if (key.carriage().equals(carriage) && driver.cabControls != null) result.put(key, driver.cabControls.state());
        });
        return result;
    }

    public Map<ControlKey, CreateCabTimetable> cabTimetables(UUID member) {
        var result = new LinkedHashMap<ControlKey, CreateCabTimetable>();
        controllers.forEach((key, driver) -> {
            if (driver.cabTimetable != null && vehicleMotion.runtime.connected(member, key.carriage())) result.put(key, driver.cabTimetable);
        });
        return result;
    }

    public CreateCabTimetable selectedCabTimetable(UUID member) {
        var selected = latestEnabledCab(member);
        return selected == null ? null : selected.getValue().cabTimetable;
    }

    public boolean cabAutomatic(ControlKey key) {
        var driver = controllers.get(key);
        return driver != null && driver.cabControls != null
                && (driver.cabControls.state().automatic() || driver.approachingStation);
    }

    public boolean isApproachingStation(ControlKey key) {
        var driver = controllers.get(key);
        return driver != null && driver.approachingStation;
    }

    public TrainControlSource controlSource(ControlKey key) {
        var driver = controllers.get(key);
        return driver != null && driver.cabControls.state().automatic()
                ? TrainControlSource.CAB_AUTOMATIC : TrainControlSource.CAB_MANUAL;
    }

    /** The latest operated enabled desk supplies keyboard steering and the selected timetable. */
    public boolean isSimpleControlSelected(UUID member) {
        var selected = latestEnabledCab(member);
        return selected != null && selected.getValue().cabControls.state().simple();
    }

    public Map.Entry<ControlKey, Controller> latestEnabledCab(UUID member) {
        Map.Entry<ControlKey, Controller> selected = null;
        for (var entry : orderedCabs(member)) {
            var module = entry.getValue().cabControls;
            if (!module.state().enabled()) continue;
            selected = entry;
        }
        return selected;
    }

    /** Left/right in the runtime's positive route direction, supplied as compatibility data only. */
    public int cabManualSteer(UUID member) {
        var selected = latestEnabledCab(member);
        if (selected == null || !selected.getValue().cabControls.state().simple()) return 0;
        var driver = selected.getValue();
        int direction = driver.cabFacing == vehicleMotion.forward(selected.getKey().carriage()) ? 1 : -1;
        return HexieBridges.cabControl().simpleCabControls().steering(driver.cabControls.keyboardMask()) * direction;
    }

    public int cabDirection(UUID member) {
        double speed = vehicleMotion.runtime.speed(member);
        if (Math.abs(speed) > 1e-5) return speed > 0 ? 1 : -1;
        var selected = latestEnabledCab(member);
        if (selected == null) return 1;
        var driver = selected.getValue();
        int direction = driver.cabFacing == vehicleMotion.forward(selected.getKey().carriage()) ? 1 : -1;
        int reverser = driver.cabControls.effectiveInput(0).reverser();
        return (reverser == 0 ? 1 : reverser) * direction;
    }

    public boolean hasSimpleCab(UUID member) {
        return controllers.entrySet().stream().anyMatch(e -> e.getKey().carriage().equals(member)
                && e.getValue().cabControls != null && e.getValue().cabControls.state().simple());
    }

    /** Both automatic operation and explicit space-to-approach use our route-based driving policy. */
    public void controlAutomatic(UUID member, AutomaticTrainControlTargetView target,
                                 double serviceDeceleration) {
        // Manual drivers receive no automatic intervention unless they explicitly hold approach.
        if (!isBackendAutomaticControlSelected(member)) return;
        var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(member);
        if (train == null) return;
        train.stationState.backendAutomaticRequest = HexieBridges.cabControl().automaticTrainControl().update(
                backendControlSource(member), vehicleMotion.runtime.speed(member), target, serviceDeceleration, stationStopState(member));
        applyControls(member);
    }

    public StationStopControlStateView stationStopState(UUID member) {
        var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(member);
        return train == null || isBackendApproachingStation(member)
                ? HexieBridges.cabControl().stationStopControlState().running()
                : train.stationStopControl().state();
    }

    public AutomaticTrainControlTargetView automaticTarget(UUID member) {
        var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(member);
        if (train == null) return null;
        int direction = train.navigation.destination != null ? train.navigation.destinationBehindTrain ? 1 : -1
                : vehicleMotion.runtime.speed(member) < 0 ? -1 : 1;
        var target = HexieBridges.cabControl().automaticTrainControl().fromRoute(train.stationState.displayRoute, direction,
                train.stationState.automaticRouteAvailable, train.stationState.waypointTransitionActive, vehicleMotion.runtime.speed(member),
                HexieBridges.cabControl().trainControlLimits().capture(vehicleMotion.runtime, member));
        return isBackendApproachingStation(member)
                ? HexieBridges.cabControl().automaticTrainControl().stationApproach(target) : target;
    }

    public AutomaticTrainControlTargetView automaticTarget(CreateCabTimetable execution,
            double speed, TrainControlLimits limits) {
        int direction = execution.navigation.destination != null ? execution.navigation.destinationBehindTrain ? 1 : -1
                : speed < 0 ? -1 : 1;
        var target = HexieBridges.cabControl().automaticTrainControl().fromRoute(execution.displayRoute, direction,
                execution.automaticRouteAvailable, execution.waypointTransitionActive, speed, limits);
        return isApproachingStation(execution.key())
                ? HexieBridges.cabControl().automaticTrainControl().stationApproach(target) : target;
    }

    /** Bind backend observations to the common controller after route collection has completed. */
    public void applyAutomaticControls() {
        var visited = new HashSet<java.util.UUID>();
        for (UUID member : vehicleMotion.carriages) {
            var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(member);
            if (train == null || !visited.add(train.id)) continue;
            boolean automatic = isBackendAutomaticControlSelected(member);
            if (!train.stationState.controlObservationsReady) {
                if (train.stationState.controlResetRequested || !automatic && train.stationState.backendAutomaticRequest != null) {
                    train.stationState.backendAutomaticRequest = null;
                    controlledCarriages.add(member);
                }
                train.stationState.controlResetRequested = false;
                train.stationState.automaticControlActive = automatic;
                continue;
            }
            var source = backendControlSource(member);
            var command = HexieBridges.cabControl().cabController().automaticOutput(source, automatic,
                    train.stationState.automaticControlActive, hasNativeManualControl(member), train.stationState.controlResetRequested,
                    vehicleMotion.runtime.speed(member), automaticTarget(member),
                    HexieBridges.cabControl().trainControl().serviceDeceleration(vehicleMotion.runtime, member, source), stationStopState(member));
            if (command != null || !automatic && train.stationState.backendAutomaticRequest != null) {
                train.stationState.backendAutomaticRequest = automatic ? command : null;
                controlledCarriages.add(member);
            }
            train.stationState.controlResetRequested = false;
            train.stationState.automaticControlActive = automatic;
        }
    }

    /** Physical station/track-end capture reports a stop; the common controller supplies its output. */
    public void applyControlStopEvents() {
        var visited = new HashSet<java.util.UUID>();
        for (UUID member : vehicleMotion.carriages) {
            var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(member);
            if (train == null || !visited.add(train.id) || !train.stationState.controlStopRequested) continue;
            train.stationState.controlStopRequested = false;
            controlAutomatic(member, null, 0);
        }
    }

    public boolean isAutomaticControlSelected(UUID member) {
        if (CreateSharedCabTimetables.hasConflict(vehicleMotion, member)) return false;
        var selected = latestEnabledCab(member);
        if (selected != null) return selected.getValue().cabControls.state().automatic() || selected.getValue().approachingStation;
        return isBackendAutomaticControlSelected(member);
    }

    /** Backend execution keeps its own mode even while a desk owns traction and the physical route. */
    public boolean isBackendAutomaticControlSelected(UUID member) {
        if (CreateSharedCabTimetables.hasConflict(vehicleMotion, member)) return false;
        return isBackendApproachingStation(member) || nativeAutomaticSelected(member);
    }

    public boolean isBackendApproachingStation(UUID member) {
        return controllers.entrySet().stream().anyMatch(entry -> vehicleMotion.runtime.connected(member, entry.getKey().carriage())
                && entry.getValue().cabControls == null && entry.getValue().attended && !entry.getValue().timedOut
                && entry.getValue().approachingStation);
    }

    public TrainControlSource backendControlSource(UUID member) {
        return hasNativeManualControl(member) ? TrainControlSource.CREATE_MANUAL : TrainControlSource.CREATE_AUTOMATIC;
    }

    public boolean nativeAutomaticSelected(UUID member) {
        if (hasNativeManualControl(member)) return false;
        var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(member);
        if (train == null || train.runtime.getSchedule() == null || train.runtime.paused || train.runtime.completed) return false;
        return train.navigation.destination == null
                ? train.hasNativeForwardConductor() || train.hasNativeBackwardConductor()
                : train.navigation.destinationBehindTrain ? train.hasNativeBackwardConductor() : train.hasNativeForwardConductor();
    }

    /** Manual station approach keeps the manual actuator limits; blaze schedules use automatic limits. */
    public TrainControlSource controlSource(UUID member) {
        if (hasEnabledCabModule(member)) return cabMode(member) == Mode.AUTOMATIC
                ? TrainControlSource.CAB_AUTOMATIC : TrainControlSource.CAB_MANUAL;
        return isManuallyControlled(member) ? TrainControlSource.CREATE_MANUAL : TrainControlSource.CREATE_AUTOMATIC;
    }

    public Mode cabMode(UUID member) {
        return cabSelection(member).mode();
    }

    public boolean isApproachingStation(UUID member) {
        var selection = cabSelection(member);
        var selected = latestEnabledCab(member);
        if (selected != null) return selected.getValue().approachingStation;
        return controllers.entrySet().stream().anyMatch(e -> vehicleMotion.runtime.connected(member, e.getKey().carriage())
                && e.getValue().approachingStation && (selection.hasEnabledCab()
                ? e.getValue().cabControls != null && e.getValue().cabControls.state().enabled()
                : e.getValue().cabControls == null && e.getValue().attended && !e.getValue().timedOut));
    }

    /** Share only the demands changed by this gesture, keeping unrelated local controls intact. */
    public void synchronizeCabDemands(ControlKey sourceKey, CabState before, CabState after,
                                       Set<Demand> operated) {
        boolean powerChanged = operated.contains(Demand.POWER) || before.input().power() != after.input().power();
        boolean brakeChanged = operated.contains(Demand.BRAKE)
                || before.input().brake() != after.input().brake() || !before.brakes().equals(after.brakes());
        boolean directionChanged = operated.contains(Demand.REVERSER)
                || before.input().reverser() != after.input().reverser();
        if (!powerChanged && !brakeChanged && !directionChanged) return;
        var source = controllers.get(sourceKey);
        int sourceDirection = source.cabFacing == vehicleMotion.forward(sourceKey.carriage()) ? 1 : -1;
        for (var entry : orderedCabs(sourceKey.carriage())) {
            if (entry.getKey().equals(sourceKey)) continue;
            var target = entry.getValue();
            var previous = target.cabControls.state();
            int targetDirection = target.cabFacing == vehicleMotion.forward(entry.getKey().carriage()) ? 1 : -1;
            var demand = new Input(powerChanged ? after.input().power() : previous.input().power(),
                    brakeChanged ? after.input().brake() : previous.input().brake(),
                    directionChanged ? after.input().reverser() * sourceDirection * targetDirection : previous.input().reverser());
            var synchronizedState = previous.withDemands(demand, brakeChanged ? after.brakes() : previous.brakes());
            target.cabControls.submit(synchronizeCabState(target, previous, synchronizedState));
            if (directionChanged && demand.reverser() == 0) releaseCabApproach(entry.getKey().carriage(), target);
        }
    }

    public static CabState synchronizeCabState(Controller driver, CabState previous, CabState source) {
        if (driver.cabBlock != null && driver.cabBlock.getBlock() instanceof CabControlHost host)
            return HexieBridges.cabControl().cabModule().synchronize(host, driver.cabBlock, previous, source, 1);
        return HexieBridges.cabControl().cabModule().synchronize(driver.cabConverter, previous, source, 1);
    }

    public void releaseCabApproach(UUID member, Controller driver) {
        if (driver.approachingStation && vehicleMotion.compatibility != null) {
            var train = vehicleMotion.compatibility.train(member);
            if (train != null) {
                java.util.function.Supplier<Void> release = () -> {
                    if (train.runtime.getSchedule() == null)
                        CreateNavigationState.cancel(train, CreateNavigationState.Reason.MANUAL_APPROACH_CANCELLED);
                    else CreateNavigationState.clearSignalWait(train);
                    return null;
                };
                if (driver.cabTimetable == null) release.get();
                else driver.cabTimetable.with(train, driver.cabTimetable == selectedCabTimetable(member), release);
            }
        }
        driver.approachHeld = false;
        driver.approachingStation = false;
        if (driver.cabControls != null) driver.cabControls.releaseApproach();
    }

    public CompoundTag cabFrame(UUID member) {
        var tag = new CompoundTag();
        var modules = new ListTag();
        controllers.forEach((key, driver) -> {
            if (!key.carriage().equals(member) || driver.cabControls == null) return;
            var entry = new CompoundTag();
            entry.putLong("Position", driver.pos.asLong()); entry.put("Controls", driver.cabControls.write());
            modules.add(entry);
        });
        tag.put("Modules", modules);
        return tag;
    }

    public void applyAllControls() {
        var visited = new HashSet<UUID>();
        for (UUID member : vehicleMotion.carriages) {
            if (visited.contains(member)) continue;
            var consist = java.util.Arrays.stream(vehicleMotion.carriages).filter(id -> vehicleMotion.runtime.connected(member, id)).toList();
            visited.addAll(consist);
            applyControls(member, consist);
        }
    }

    public void applyControls(UUID member) {
        applyControls(member, java.util.Arrays.stream(vehicleMotion.carriages).filter(id -> vehicleMotion.runtime.connected(member, id)).toList());
    }

    public void applyControls(UUID member, List<UUID> consist) {
        vehicleMotion.applyCouplingControlResets();
        var cabs = orderedCabs(member);
        boolean timetableConflict = CreateSharedCabTimetables.hasConflict(vehicleMotion, member);
        var selected = latestEnabledCab(member);
        var selection = HexieBridges.cabControl().cabControlArbitration().select(cabs.stream().map(e -> e.getValue().cabControls.state()).toList());
        var limits = HexieBridges.cabControl().trainControlLimits().capture(vehicleMotion.runtime, member);
        double deceleration = HexieBridges.cabControl().trainControl().serviceDeceleration(vehicleMotion.runtime, member, TrainControlSource.CAB_MANUAL);
        var commands = new ArrayList<CabControlArbitrationCommandView>();
        for (var e : cabs) {
            UUID carriage = e.getKey().carriage();
            var driver = e.getValue();
            var input = driver.cabControls;
            var player = vehicleMotion.level.getPlayerByUUID(driver.player);
            var cab = DrivingConsoleContext.find(player);
            boolean acceptsHeldKeys = driver.attended && cab != null && cab.console().equals(driver.pos)
                    && cab.seatAccess().vehicle().getUUID().equals(carriage)
                    && vehicleMotion.level.getGameTime() - driver.lastKeyboardInput <= 20;
            if (!acceptsHeldKeys) input.keyboard(0);
            // Occupancy only authorizes gestures. Enable inputs and stored handles persist unattended.
            if (!driver.attended || cab == null
                    || !cab.console().equals(driver.pos) || !cab.seatAccess().vehicle().getUUID().equals(carriage)
                    || driver.approachHeld && vehicleMotion.level.getGameTime() - driver.lastApproachInput > 20)
                releaseCabApproach(carriage, driver);
            int direction = driver.cabFacing == vehicleMotion.forward(carriage) ? 1 : -1;
            var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(carriage);
            boolean controlling = selected != null && selected.getValue() == driver;
            if (controlling && !timetableConflict && driver.approachHeld
                    && (input.state().simple() || !input.state().automatic()) && train != null)
                driver.cabTimetable.with(train, driver.cabTimetable == selectedCabTimetable(carriage), () ->
                        vehicleMotion.controlStationApproach(train, driver, player, true,
                                input.effectiveInput(vehicleMotion.runtime.speed(carriage) * direction).reverser() * direction,
                                vehicleMotion.runtime.speed(carriage), direction > 0));
            var target = automaticTarget(driver.cabTimetable, vehicleMotion.runtime.speed(carriage), limits);
            boolean approaching = driver.approachingStation
                    || driver.approachHeld && driver.cabTimetable.stationStop.state().waitingForDeparture();
            // Range arrival is a notification, not completion of the held Space docking movement.
            var stop = driver.approachingStation
                    ? HexieBridges.cabControl().stationStopControlState().running()
                    : driver.cabTimetable.stationStop.state();
            var command = !controlling ? HexieBridges.cabControl().cabController().manual(input.state().input().direction(direction)) : input.state().simple()
                    ? input.updateSimple(approaching, direction, vehicleMotion.runtime.speed(carriage), target, deceleration, stop)
                    : input.update(approaching ? Mode.AUTOMATIC : input.mode(), direction,
                            vehicleMotion.runtime.speed(carriage), target, deceleration, stop);
            command = input.convertOutput(driver.cabConverter, command, direction, vehicleMotion.runtime.speed(carriage),
                    controlling, input.state().automatic() || approaching);
            if (timetableConflict && (input.state().automatic() || approaching))
                command = new TrainCommand(0, input.state().input().brake());
            commands.add(HexieBridges.cabControl().createCabControlArbitrationCommand(controlling, command));
        }
        var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(member);
        if (train != null && train.stationState.backendAutomaticRequest != null && !isBackendAutomaticControlSelected(member))
            train.stationState.backendAutomaticRequest = null;
        var automatic = train == null ? null : train.stationState.backendAutomaticRequest;
        long latestInput = -1;
        double nativePower = 0;
        double nativeBrake = 0;
        for (var entry : controllers.entrySet()) {
            var driver = entry.getValue();
            if (driver.nativeRequest == null || !consist.contains(entry.getKey().carriage())) continue;
            nativeBrake = Math.max(nativeBrake, driver.nativeRequest.brake());
            if (driver.inputSequence > latestInput) {
                latestInput = driver.inputSequence;
                nativePower = driver.nativeRequest.throttle()
                        * (driver.nativeBasis == vehicleMotion.forward(entry.getKey().carriage()) ? 1 : -1);
            }
        }
        var backend = automatic == null ? new TrainCommand(nativePower, nativeBrake)
                : new TrainCommand(automatic.throttle(), Math.max(automatic.brake(), nativeBrake));
        // Supply the same consist brake feedback to each vehicle/desk conversion before aggregation.
        double consistBrake = backend.brake();
        for (var command : commands) consistBrake = Math.max(consistBrake, command.control().brake());
        for (int i = 0; i < commands.size(); i++) {
            var command = commands.get(i);
            var converter = cabs.get(i).getValue().cabConverter;
            double power = converter.tractionOutput(command.control().throttle(), consistBrake);
            commands.set(i, HexieBridges.cabControl().createCabControlArbitrationCommand(command.enabled(),
                    new TrainCommand(power, command.control().brake())));
        }
        // Create's own controls retain their brake-priority traction policy in this adapter.
        if (consistBrake > 0) backend = new TrainCommand(0, backend.brake());
        var output = HexieBridges.cabControl().cabControlArbitration().aggregate(commands, backend);
        boolean derailed = consist.stream().anyMatch(vehicleMotion.derailedCarriages::contains) || vehicleMotion.isDerailed(member);
        if (derailed) output = new TrainCommand(0, output.brake());
        boolean active = derailed || selection.hasEnabledCab() || automatic != null || latestInput >= 0 || output.brake() > 0;
        if (active || consist.stream().anyMatch(controlledCarriages::contains))
            vehicleMotion.runtime.controlsForConsist(member, output);
        if (active) controlledCarriages.addAll(consist);
        else controlledCarriages.removeAll(consist);
    }

    public void releaseCompatibilityControl(UUID carriage) {
        if (hasEnabledCabModule(carriage) || hasNativeManualControl(carriage)) return;
        var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(carriage);
        if (train == null) return;
        train.stationState.backendAutomaticRequest = null;
        train.manualSteer = SteerDirection.NONE;
        train.manualTick = false;
        if (train.runtime.getSchedule() == null)
            CreateNavigationState.cancel(train, CreateNavigationState.Reason.MODE_CHANGED);
        else CreateNavigationState.clearSignalWait(train);
    }

    public static final class Controller {
        public UUID player;
        public boolean attended = true;
        public Direction cabFacing = Direction.NORTH;
        public final BlockPos pos;
        public long lastInput;
        public long lastKeyboardInput;
        public long nextHud;
        public boolean timedOut;
        public boolean approachingStation;
        public boolean approachHeld;
        public long lastApproachInput;
        public boolean stationMessage;
        // Native key requests are transient; physical desk inputs survive save/load in cabControls.
        public TrainCommand nativeRequest;
        public Direction nativeBasis;
        public long inputSequence;
        public CabTrainControl cabConverter = CabTrainControl.NONE;
        public net.minecraft.world.level.block.state.BlockState cabBlock;
        public CabModuleView cabControls;
        public CreateCabTimetable cabTimetable;
        public Controller(UUID player, BlockPos pos, long lastInput) {
            this.player = player; this.pos = pos; this.lastInput = lastInput;
        }
    }
}
