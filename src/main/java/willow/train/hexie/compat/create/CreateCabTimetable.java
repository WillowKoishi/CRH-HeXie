package willow.train.hexie.compat.create;

import java.util.HashSet;
import java.util.function.Supplier;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.TrainStatus;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import willow.train.hexie.api.cab.CabTimetableHost;
import willow.train.hexie.vehicle.control.StationStopControl;
import willow.train.hexie.vehicle.control.TrainDisplayReadings;

/** One console's existing Create execution objects, bound to the consist only while they are being used. */
final class CreateCabTimetable {
    static final String EXECUTION = "CabTimetableExecution";
    private final AssembledVehicleMotion.ControlKey key;
    final ScheduleRuntime runtime;
    final Navigation navigation;
    final StationStopControl stationStop = new StationStopControl();
    TrainDisplayReadings.Route displayRoute = TrainDisplayReadings.Route.unavailable();
    boolean automaticRouteAvailable;
    boolean controlObservationsReady;
    int waypointTransitionTicks;
    boolean waypointTransitionActive;
    double throttle = 1;
    private TrainStatus status;
    private CompoundTag pendingNavigation;
    private DimensionPalette pendingDimensions;
    private boolean publishing;
    private boolean newConsistEnds;

    CreateCabTimetable(AssembledVehicleMotion.ControlKey key, HolderLookup.Provider registries, CompoundTag blockData) {
        this.key = key;
        runtime = new CabRuntime();
        navigation = new CabNavigation();
        var stored = blockData == null ? new CompoundTag() : blockData.getCompound(CabTimetableHost.PENDING_TIMETABLE);
        var execution = stored.contains(EXECUTION) ? stored.getCompound(EXECUTION)
                : blockData == null ? new CompoundTag() : blockData.getCompound(EXECUTION);
        var runtimeData = execution.contains("Runtime") ? execution.getCompound("Runtime").copy() : initialRuntime(stored);
        if (!stored.isEmpty()) runtimeData.put("Schedule", stored);
        runtime.read(registries, runtimeData);
        runtime.currentTitle = execution.getString("CurrentTitle");
        runtime.ticksInTransit = execution.getInt("TicksInTransit");
        if (execution.contains("Throttle")) throttle = execution.getDouble("Throttle");
        waypointTransitionTicks = execution.getInt("WaypointTransitionTicks");
        newConsistEnds = execution.getBoolean("NewConsistEnds");
        var stop = execution.getCompound("StationStop");
        if (stop.getBoolean("Waiting") && !stop.getString("Station").isEmpty())
            stationStop.restore(new StationStopControl.State(true, stop.getString("Station"), stop.getString("Name")));
        if (execution.contains("Navigation")) {
            pendingNavigation = execution.getCompound("Navigation").copy();
            if (!execution.contains(CompatibilityTrain.NATIVE_WHEEL_ORDER))
                CompatibilityTrain.reverseSavedNavigation(pendingNavigation);
            pendingDimensions = DimensionPalette.read(execution);
        }
    }

    static CreateCabTimetable read(AssembledVehicleMotion.ControlKey key, HolderLookup.Provider registries, CompoundTag saved) {
        return new CreateCabTimetable(key, registries, saved);
    }

    AssembledVehicleMotion.ControlKey key() { return key; }
    boolean publishing() { return publishing; }
    boolean hasTimetable() { return runtime.getSchedule() != null; }

    /** Track invalidation interrupts this execution, without removing its timetable or changing another cab. */
    void invalidateNavigation() {
        pendingNavigation = null;
        pendingDimensions = null;
        newConsistEnds = false;
        boolean before = publishing;
        publishing = false;
        try { navigation.cancelNavigation(); }
        finally { publishing = before; }
        // An unresolved saved destination can be absent from the live Navigation already.
        runtime.transitInterrupted();
        navigation.waitingForSignal = null;
        navigation.ticksWaitingForSignal = 0;
        navigation.distanceToSignal = Double.MAX_VALUE;
        automaticRouteAvailable = false;
        controlObservationsReady = false;
        waypointTransitionTicks = 0;
        waypointTransitionActive = false;
        displayRoute = TrainDisplayReadings.Route.unavailable();
    }

    /** The same invalidation before saved console executions have been materialized. */
    static void invalidateSavedNavigation(CompoundTag data) {
        var stored = data.getCompound(CabTimetableHost.PENDING_TIMETABLE);
        var execution = stored.contains(EXECUTION) ? stored.getCompound(EXECUTION) : data.getCompound(EXECUTION);
        execution.remove("Navigation");
        execution.remove("NewConsistEnds");
        execution.remove("WaypointTransitionTicks");
        var runtime = execution.getCompound("Runtime");
        if (ScheduleRuntime.State.IN_TRANSIT.name().equals(runtime.getString("State")))
            runtime.putString("State", ScheduleRuntime.State.PRE_TRANSIT.name());
    }

    /** Rebinding retains the execution objects and their native/RailX extensions. */
    void bind(CompatibilityTrain train) {
        if (runtime.train instanceof CompatibilityTrain previous && !previous.carriageIds.equals(train.carriageIds))
            newConsistEnds = true;
        if (runtime.train != null && runtime.train != train && pendingNavigation == null && train.graph == null) {
            pendingDimensions = new DimensionPalette();
            pendingNavigation = navigation.write(pendingDimensions);
        }
        if (runtime.train != train || status == null) status = new TrainStatus(train);
        runtime.train = train;
        navigation.train = train;
        if (pendingNavigation != null && train.graph != null) {
            navigation.read(pendingNavigation, train.graph, pendingDimensions);
            if (!pendingNavigation.contains("Destination") || navigation.destination != null) {
                pendingNavigation = null;
                pendingDimensions = null;
            }
        }
    }

    /** The new leading end may be a different carriage, or face the other way. Keep the same schedule entry. */
    void measureFromConsistEnds() {
        if (!newConsistEnds || pendingNavigation != null) return;
        newConsistEnds = false;
        var destination = navigation.destination;
        if (destination == null) return;
        // A coupled carriage can become the opposite end. Create otherwise restricts this search to the old direction.
        com.simibubi.create.content.trains.graph.DiscoveredPath path;
        navigation.destination = null;
        try { path = navigation.findPathTo(destination, Double.MAX_VALUE); }
        finally { navigation.destination = destination; }
        if (path == null) navigation.cancelNavigation();
        else navigation.startNavigation(path);
    }

    CompoundTag timetable(HolderLookup.Provider registries) {
        var schedule = runtime.getSchedule();
        if (schedule == null) return new CompoundTag();
        var data = schedule.write(registries);
        data.putInt("Progress", runtime.currentEntry);
        return data;
    }

    CompoundTag write(HolderLookup.Provider registries) {
        var execution = new CompoundTag();
        execution.putBoolean(CompatibilityTrain.NATIVE_WHEEL_ORDER, true);
        var run = runtime.write(registries);
        var stored = run.getCompound("Schedule");
        if (!stored.isEmpty()) stored.putInt("Progress", runtime.currentEntry);
        run.remove("Schedule");
        execution.put("Runtime", run);
        execution.putString("CurrentTitle", runtime.currentTitle == null ? "" : runtime.currentTitle);
        execution.putInt("TicksInTransit", runtime.ticksInTransit);
        execution.putDouble("Throttle", throttle);
        execution.putInt("WaypointTransitionTicks", waypointTransitionTicks);
        if (newConsistEnds) execution.putBoolean("NewConsistEnds", true);
        var stop = stationStop.state();
        var stopData = new CompoundTag();
        stopData.putBoolean("Waiting", stop.waitingForDeparture());
        stopData.putString("Station", stop.stationId());
        stopData.putString("Name", stop.stationName());
        execution.put("StationStop", stopData);
        var dimensions = pendingNavigation != null ? pendingDimensions : new DimensionPalette();
        execution.put("Navigation", pendingNavigation != null ? pendingNavigation.copy() : navigation.write(dimensions));
        dimensions.write(execution);
        var data = new CompoundTag();
        if (stored.isEmpty()) data.put(EXECUTION, execution);
        else {
            stored.put(EXECUTION, execution);
            data.put(CabTimetableHost.PENDING_TIMETABLE, stored);
        }
        return data;
    }

    StructureBlockInfo withBlockData(StructureBlockInfo info, HolderLookup.Provider registries) {
        var data = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
        data.remove(EXECUTION);
        if (hasTimetable())
            data.put(CabTimetableHost.PENDING_TIMETABLE, write(registries).getCompound(CabTimetableHost.PENDING_TIMETABLE));
        else data.remove(CabTimetableHost.PENDING_TIMETABLE);
        return data.equals(info.nbt()) ? info : new StructureBlockInfo(info.pos(), info.state(), data);
    }

    void replace(Schedule schedule, HolderLookup.Provider registries) {
        if (runtime.train instanceof CompatibilityTrain train) {
            with(train, false, () -> {
                if (schedule == null) runtime.discardSchedule();
                else {
                    navigation.cancelNavigation();
                    runtime.setSchedule(schedule, false);
                }
                return null;
            });
        } else {
            var stored = schedule == null ? new CompoundTag() : schedule.write(registries);
            runtime.read(registries, initialRuntime(stored));
        }
        runtime.currentTitle = "";
        runtime.ticksInTransit = 0;
        pendingNavigation = null;
        pendingDimensions = null;
        newConsistEnds = false;
        stationStop.clear();
        automaticRouteAvailable = false;
        controlObservationsReady = false;
        waypointTransitionTicks = 0;
        waypointTransitionActive = false;
        displayRoute = TrainDisplayReadings.Route.unavailable();
    }

    /** Import the previous single-console runtime once, before native execution resumes independently. */
    void adoptLegacy(CompatibilityTrain train, HolderLookup.Provider registries) {
        runtime.read(registries, train.runtime.write(registries));
        runtime.currentTitle = train.runtime.currentTitle;
        runtime.ticksInTransit = train.runtime.ticksInTransit;
        var route = CreateConsoleTimetable.legacyNavigation(train);
        pendingDimensions = DimensionPalette.read(route);
        pendingNavigation = route.getCompound("Navigation");
        throttle = train.throttle;
        stationStop.restore(train.stationStopControl().state());
        waypointTransitionTicks = train.waypointTransitionTicks;
        bind(train);
    }

    <T> T with(CompatibilityTrain train, boolean publish, Supplier<T> action) {
        long navigationRevision = train.navigationInvalidation;
        if (train.currentCabTimetable == this) {
            boolean before = publishing;
            var reservations = train.reservedSignalBlocks;
            if (!publish && before) train.reservedSignalBlocks = new HashSet<>(reservations);
            publishing = before && publish;
            try { return action.get(); }
            finally {
                publishing = before;
                if (navigationRevision != train.navigationInvalidation) reservations.clear();
                train.reservedSignalBlocks = reservations;
            }
        }
        bind(train);
        var previous = train.currentCabTimetable;
        var originalRuntime = train.runtime;
        var originalNavigation = train.navigation;
        var originalStatus = train.status;
        var originalReservations = train.reservedSignalBlocks;
        double originalThrottle = train.throttle;
        boolean originalManual = train.manualTick;
        var originalRoute = train.displayRoute;
        boolean originalAvailable = train.automaticRouteAvailable;
        boolean originalReady = train.controlObservationsReady;
        int originalTransitionTicks = train.waypointTransitionTicks;
        boolean originalTransition = train.waypointTransitionActive;
        boolean previousPublish = publishing;
        train.currentCabTimetable = this;
        train.runtime = runtime;
        train.navigation = navigation;
        train.status = status;
        train.throttle = throttle;
        train.displayRoute = displayRoute;
        train.automaticRouteAvailable = automaticRouteAvailable;
        train.controlObservationsReady = controlObservationsReady;
        train.waypointTransitionTicks = waypointTransitionTicks;
        train.waypointTransitionActive = waypointTransitionActive;
        publishing = publish;
        if (!publish) train.reservedSignalBlocks = new HashSet<>(originalReservations);
        try { return action.get(); }
        finally {
            throttle = train.throttle;
            status = train.status;
            displayRoute = train.displayRoute;
            automaticRouteAvailable = train.automaticRouteAvailable;
            controlObservationsReady = train.controlObservationsReady;
            waypointTransitionTicks = train.waypointTransitionTicks;
            waypointTransitionActive = train.waypointTransitionActive;
            train.runtime = originalRuntime;
            train.navigation = originalNavigation;
            train.status = originalStatus;
            train.reservedSignalBlocks = originalReservations;
            train.throttle = originalThrottle;
            train.manualTick = originalManual;
            train.displayRoute = originalRoute;
            train.automaticRouteAvailable = originalAvailable;
            train.controlObservationsReady = originalReady;
            train.waypointTransitionTicks = originalTransitionTicks;
            train.waypointTransitionActive = originalTransition;
            train.currentCabTimetable = previous;
            publishing = previousPublish;
            if (navigationRevision != train.navigationInvalidation) {
                // Native detach may run while a cab temporarily hides the backend execution.
                // Cancel it only after restoring its matching runtime, and do not resurrect observations.
                if (train.navigation.destination != null) train.navigation.cancelNavigation();
                train.reservedSignalBlocks.clear();
                CreateConsoleTimetable.invalidateNavigationObservations(train);
            }
        }
    }

    private static CompoundTag initialRuntime(CompoundTag timetable) {
        var data = new CompoundTag();
        data.putString("State", ScheduleRuntime.State.PRE_TRANSIT.name());
        data.putBoolean("Paused", timetable.isEmpty());
        if (!timetable.isEmpty()) data.put("Schedule", timetable);
        int entries = timetable.getList("Entries", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
        data.putInt("CurrentEntry", Math.clamp(timetable.getInt("Progress"), 0, Math.max(0, entries - 1)));
        return data;
    }

    private final class CabRuntime extends ScheduleRuntime {
        CabRuntime() { super(null); }
        @Override public ItemStack returnSchedule(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    }

    private final class CabNavigation extends Navigation {
        CabNavigation() { super(null); }

        @Override public void cancelNavigation() {
            if (publishing) { super.cancelNavigation(); return; }
            boolean interrupted = destination != null;
            // Skip only the shared station reservation; still run native and RailX cancellation hooks.
            destination = null;
            super.cancelNavigation();
            if (interrupted) runtime.transitInterrupted();
        }
    }
}
