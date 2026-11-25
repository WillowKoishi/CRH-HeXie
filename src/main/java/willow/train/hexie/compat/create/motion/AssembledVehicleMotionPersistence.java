package willow.train.hexie.compat.create.motion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabModuleView;
import willow.train.hexie.bridge.vehicle.CarriageSnapshotView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageSnapshotStoreActiveAssemblyView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleConstraintSolverConstraintFailureView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleRuntimeRouteMismatchExceptionView;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.compat.create.interaction.AssembledVehicleControlInput;
import willow.train.hexie.compat.create.signal.CreateCabTimetable;
import willow.train.hexie.compat.create.signal.CreateConsoleTimetableStorage;
import willow.train.hexie.compat.create.signal.CreateRouteBinding;
import willow.train.hexie.compat.create.signal.CreateRoutePersistence;
import willow.train.hexie.core.api.cab.CabControlHost;
import willow.train.hexie.core.api.traincontrol.TrainCommand;

// split from AssembledVehicleMotion.java
public final class AssembledVehicleMotionPersistence {
    private final AssembledVehicleMotion vehicleMotion;

    public AssembledVehicleMotionPersistence(AssembledVehicleMotion vehicleMotion) { this.vehicleMotion = vehicleMotion; }

    /** Capture every cab once, including enabled consoles which have not received a player packet. */
    public void importCabModules(CarriageSnapshotView snapshot) {
        for (var block : snapshot.blocks()) {
            if (!(block.state().getBlock() instanceof CabControlHost host)) continue;
            var key = new AssembledVehicleCabControl.ControlKey(snapshot.id(), block.position());
            if (vehicleMotion.cabControl.controllers.containsKey(key)) {
                var existing = vehicleMotion.cabControl.controllers.get(key);
                existing.cabConverter = host.trainControl(block.state());
                existing.cabBlock = block.state();
                if (existing.cabControls != null)
                    existing.cabControls.submit(HexieBridges.cabControl().cabModule().normalize(existing.cabConverter, existing.cabControls.state()));
                if (existing.cabTimetable == null)
                    existing.cabTimetable = new CreateCabTimetable(key, vehicleMotion.level.registryAccess(),
                            CreateConsoleTimetableStorage.storedBlockData(vehicleMotion.level, key, block.entityData()));
                continue;
            }
            var facing = host.cabFacing(block.state());
            if (facing == null || !facing.getAxis().isHorizontal()) continue;
            var driver = new AssembledVehicleCabControl.Controller(new UUID(0, 0), block.position(), vehicleMotion.level.getGameTime());
            driver.attended = false;
            driver.cabFacing = facing;
            driver.cabConverter = host.trainControl(block.state());
            driver.cabBlock = block.state();
            var data = block.entityData();
            driver.cabControls = data != null && (data.contains("CabControls") || data.contains("TestControls"))
                    ? HexieBridges.cabControl().cabModule().read(data.getCompound(data.contains("CabControls") ? "CabControls" : "TestControls"))
                    : (CabModuleView) host.createCabModule();
            driver.cabControls.submit(HexieBridges.cabControl().cabModule().normalize(driver.cabConverter, driver.cabControls.state()));
            driver.cabTimetable = new CreateCabTimetable(key, vehicleMotion.level.registryAccess(), data);
            vehicleMotion.cabControl.controllers.put(key, driver);
        }
    }

    public CompoundTag writeState() {
        vehicleMotion.applyCouplingControlResets();
        var tag = new CompoundTag();
        tag.putInt("Version", 2);
        tag.putUUID("Assembly", vehicleMotion.assembly);
        tag.putString("Forward", vehicleMotion.forward.getName());
        var origins = new ListTag();
        for (UUID id : vehicleMotion.carriages) {
            var origin = new CompoundTag();
            origin.putUUID("Carriage", id); origin.putUUID("Assembly", vehicleMotion.sourceAssembly(id));
            origin.putString("Forward", vehicleMotion.forward(id).getName());
            origin.putString("ModelForward", vehicleMotion.sources.get(id).modelForward().getName()); origins.add(origin);
        }
        tag.put("CarriageSources", origins);
        tag.put("Route", vehicleMotion.route.save());
        tag.put("Runtime", HexieBridges.vehicle().vehicleRuntimeNbt().save(vehicleMotion.runtime));
        var derailed = new ListTag();
        vehicleMotion.derailedCarriages.stream().sorted().forEach(id -> {
            var entry = new CompoundTag(); entry.putUUID("Carriage", id); derailed.add(entry);
        });
        tag.put("DerailedCarriages", derailed);
        var cabModules = new ListTag();
        vehicleMotion.cabControl.controllers.forEach((id, driver) -> {
            if (driver.cabControls == null) return;
            var saved = new CompoundTag();
            saved.putUUID("Carriage", id.carriage()); saved.putLong("Position", driver.pos.asLong());
            saved.putString("Facing", driver.cabFacing.getName()); saved.put("Controls", driver.cabControls.write());
            saved.putLong("InputSequence", driver.inputSequence);
            if (driver.cabTimetable != null) saved.put("TimetableExecution", driver.cabTimetable.write(vehicleMotion.level.registryAccess()));
            cabModules.add(saved);
        });
        tag.put("CabModules", cabModules);
        return tag;
    }

    public static AssembledVehicleMotion readState(ServerLevel level, CompoundTag tag) {
        if ((tag.getInt("Version") != 1 && tag.getInt("Version") != 2) || !tag.hasUUID("Assembly")
                || !tag.contains("Route", Tag.TAG_COMPOUND) || !tag.contains("Runtime", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid assembled motion state");
        UUID assembly = tag.getUUID("Assembly");
        var sources = readSources(level, tag);
        Direction forward = Direction.byName(tag.getString("Forward"));
        if (forward == null || !forward.getAxis().isHorizontal())
            throw new IllegalArgumentException("Invalid saved motion coordinate basis");
        final CreateRouteBinding savedRoute;
        final CreateRouteBinding route;
        try {
            savedRoute = CreateRoutePersistence.load(level, tag.getCompound("Route"));
            route = savedRoute.canonicalized();
        }
        catch (IllegalArgumentException | IllegalStateException failure) { throw new AssembledVehicleMotion.RouteUnavailableException(failure); }
        final VehicleRuntimeView runtime;
        try {
            var loaded = HexieBridges.vehicle().vehicleRuntimeNbt().load(tag.getCompound("Runtime"), savedRoute.path());
            runtime = route == savedRoute ? loaded : loaded.remapRoute(route.path(),
                    distance -> route.distanceOf(savedRoute.pointAt(distance)));
        }
        catch (VehicleRuntimeRouteMismatchExceptionView | CreateRouteBinding.RouteInvalidException | VehicleConstraintSolverConstraintFailureView failure) {
            // Nothing is installed yet. Retain the saved motion for the existing retry
            // path instead of letting an unsatisfied saved placement abort the server tick.
            throw new AssembledVehicleMotion.RouteUnavailableException(failure);
        }
        var carriages = runtime.definitions().stream().map(car -> car.id()).toList();
        if (carriages.isEmpty() || !sources.keySet().equals(Set.copyOf(carriages)))
            throw new IllegalArgumentException("Saved motion carriages disagree with their snapshots");
        // A disconnected driver's last keypress must not become permanent throttle after load.
        carriages.forEach(id -> runtime.controls(id, TrainCommand.NEUTRAL));
        var motion = new AssembledVehicleMotion(level, assembly, forward, route, runtime, sources);
        // Old StalledCarriages is intentionally ignored; Create's carriage owns actor stalls.
        for (var element : tag.getList("DerailedCarriages", Tag.TAG_COMPOUND)) {
            UUID id = ((CompoundTag) element).getUUID("Carriage");
            if (!carriages.contains(id) || !motion.derailedCarriages.add(id))
                throw new IllegalArgumentException("Invalid derailed carriage in motion save");
        }
        motion.cabControl.controllers.putAll(readCabModules(level, tag, Set.copyOf(carriages)));
        var store = HexieBridges.vehicle().carriageSnapshotStore().get(level);
        carriages.forEach(id -> store.find(id).ifPresent(motion::importCabModules));
        return motion;
    }

    public static Map<AssembledVehicleCabControl.ControlKey, AssembledVehicleCabControl.Controller> readCabModules(ServerLevel level, CompoundTag tag, Set<UUID> carriages) {
        var modules = new HashMap<AssembledVehicleCabControl.ControlKey, AssembledVehicleCabControl.Controller>();
        for (var element : tag.getList(tag.contains("CabModules") ? "CabModules" : "AutomaticCabs", Tag.TAG_COMPOUND)) {
            var saved = (CompoundTag) element;
            UUID id = saved.getUUID("Carriage");
            var key = new AssembledVehicleCabControl.ControlKey(id, BlockPos.of(saved.getLong("Position")));
            var direction = Direction.byName(saved.getString("Facing"));
            var input = HexieBridges.cabControl().cabModule().read(saved.getCompound("Controls"));
            if (!carriages.contains(id) || modules.containsKey(key) || direction == null
                    || !direction.getAxis().isHorizontal())
                throw new IllegalArgumentException("Invalid cab module in motion save");
            var driver = new AssembledVehicleCabControl.Controller(new UUID(0, 0), BlockPos.of(saved.getLong("Position")), level.getGameTime());
            driver.attended = false; driver.cabFacing = direction; driver.cabControls = input;
            driver.inputSequence = Math.max(0, saved.getLong("InputSequence"));
            AssembledVehicleControlInput.NATIVE_INPUT_SEQUENCE.accumulateAndGet(driver.inputSequence, Math::max);
            if (saved.contains("TimetableExecution"))
                driver.cabTimetable = CreateCabTimetable.read(key, level.registryAccess(), saved.getCompound("TimetableExecution"));
            modules.put(key, driver);
        }
        return modules;
    }

    /** Decode ownership links independently of whether their snapshot store is available. */
    public static Map<UUID, AssembledVehicleMotion.Source> readSavedSources(CompoundTag tag) {
        var result = new LinkedHashMap<UUID, AssembledVehicleMotion.Source>();
        if (tag.getInt("Version") == 1) {
            var direction = Direction.byName(tag.getString("Forward"));
            for (var value : tag.getCompound("Runtime").getList("Carriages", Tag.TAG_COMPOUND))
                result.put(((CompoundTag) value).getUUID("Id"), new AssembledVehicleMotion.Source(tag.getUUID("Assembly"), direction));
        } else if (tag.getInt("Version") == 2) {
            for (var value : tag.getList("CarriageSources", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) value;
                if (!entry.hasUUID("Carriage") || !entry.hasUUID("Assembly")
                        || result.putIfAbsent(entry.getUUID("Carriage"), new AssembledVehicleMotion.Source(entry.getUUID("Assembly"),
                                Direction.byName(entry.getString("Forward")))) != null)
                    throw new IllegalArgumentException("Invalid saved carriage source");
            }
        } else throw new IllegalArgumentException("Unknown motion save version");
        var expected = new HashSet<UUID>();
        for (var value : tag.getCompound("Runtime").getList("Carriages", Tag.TAG_COMPOUND))
            if (!expected.add(((CompoundTag) value).getUUID("Id")))
                throw new IllegalArgumentException("Duplicate saved carriage");
        if (expected.isEmpty() || !expected.equals(result.keySet()))
            throw new IllegalArgumentException("Saved carriage sources are incomplete");
        for (var source : result.values())
            if (source.forward() == null || !source.forward().getAxis().isHorizontal())
                throw new IllegalArgumentException("Invalid saved carriage coordinate basis");
        return result;
    }

    /** Resolve current snapshot ownership, then update the saved links without changing runtime coordinates. */
    public static Map<UUID, AssembledVehicleMotion.Source> readSources(ServerLevel level, CompoundTag tag) {
        var result = readSavedSources(tag);
        var expected = result.keySet();
        var store = HexieBridges.vehicle().carriageSnapshotStore().get(level);
        var owners = new HashMap<UUID, CarriageSnapshotStoreActiveAssemblyView>();
        for (var active : store.active()) for (UUID id : active.carriages()) {
            if (expected.contains(id) && owners.putIfAbsent(id, active) != null)
                throw new AssembledVehicleMotion.SourceUnavailableException("Saved carriage " + id + " has multiple current source assemblies");
        }
        result.forEach((id, source) -> {
            var active = owners.get(id);
            if (active == null || store.find(id).isEmpty())
                throw new AssembledVehicleMotion.SourceUnavailableException("Saved carriage " + id + " has no current source snapshot owner");
            // Forward belongs to the checkpoint's coordinate system, which may have been reversed
            // or rebased independently of the snapshot's model axes. Updating ownership must not
            // rotate its solved body or reinterpret its mounts in the current model's basis.
            result.put(id, new AssembledVehicleMotion.Source(active.id(), source.forward(), active.forward()));
        });
        var normalized = new ListTag();
        for (var entry : result.entrySet()) {
            var source = new CompoundTag();
            source.putUUID("Carriage", entry.getKey());
            source.putUUID("Assembly", entry.getValue().assembly());
            source.putString("Forward", entry.getValue().forward().getName());
            source.putString("ModelForward", entry.getValue().modelForward().getName());
            normalized.add(source);
        }
        // Publish the refreshed links only after the whole saved domain has resolved. The
        // checkpoint, native carriage records and mounted inventories are left untouched.
        tag.putInt("Version", 2);
        tag.put("CarriageSources", normalized);
        return result;
    }

    /** Fresh save data for a captured cab; null means this position has never supplied a module. */
    public CompoundTag cabControlsForSave(UUID member, BlockPos position) {
        var driver = vehicleMotion.cabControl.controllers.get(new AssembledVehicleCabControl.ControlKey(member, position));
        return driver == null || driver.cabControls == null ? null : driver.cabControls.write();
    }
}
