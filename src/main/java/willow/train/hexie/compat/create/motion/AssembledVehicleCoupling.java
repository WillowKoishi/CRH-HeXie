package willow.train.hexie.compat.create.motion;

import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingEndpointView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleRuntimeCheckpointView;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.bridge.world.CouplerBlockBridge.CouplerBlockView;
import willow.train.hexie.compat.create.signal.CreateSharedCabTimetables;
import willow.train.hexie.core.api.cab.CabState.BrakeState;
import willow.train.hexie.core.api.cab.CabState.Input;
import willow.train.hexie.core.api.traincontrol.TrainCommand;

// split from AssembledVehicleMotion.java
public final class AssembledVehicleCoupling {
    private final AssembledVehicleMotion vehicleMotion;

    public AssembledVehicleCoupling(AssembledVehicleMotion vehicleMotion) { this.vehicleMotion = vehicleMotion; }

    private final Map<UUID, BlockPos> couplingPositions = new HashMap<>();

    public boolean couplerAvailable(CouplingEndpointView endpoint) {
        var visual = endpoint.visual();
        if (visual == null) return true;
        var id = net.minecraft.resources.ResourceLocation.tryParse(visual.fixed().block());
        var block = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
        if (!(block instanceof CouplerBlockView hook) || !hook.requiresReadyState()) return true;
        if (!hook.allowsAdapters() && endpoint.adapter() != null) return false;
        var info = couplerInfo(endpoint, block);
        return info != null && hook.readyForCoupling(info.state(), info.nbt(), vehicleMotion.level.getGameTime());
    }

    /** A missing/unloaded owner is unknown, not a request to disconnect its train. */
    public boolean couplerRequiresRelease(CouplingEndpointView endpoint) {
        var visual = endpoint.visual();
        if (visual == null) return false;
        var id = net.minecraft.resources.ResourceLocation.tryParse(visual.fixed().block());
        var block = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
        if (!(block instanceof CouplerBlockView hook) || !hook.hasStateDrivenRelease()) return false;
        var info = couplerInfo(endpoint, block);
        return info != null && hook.releaseForState(info.state(), info.nbt());
    }

    public net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo couplerInfo(
            CouplingEndpointView endpoint, net.minecraft.world.level.block.Block block) {
        for (UUID carriage : vehicleMotion.carriages) {
            if (vehicleMotion.runtime.definition(carriage).couplings().stream().noneMatch(e -> e.id().equals(endpoint.id()))) continue;
            if (!(vehicleMotion.level.getEntity(carriage) instanceof SnapshotCarriageEntity entity) || entity.getContraption() == null) return null;
            BlockPos position = couplingPositions.get(endpoint.id());
            if (position == null) {
                position = entity.couplerAnimation().parameters().stream()
                        .filter(value -> value.endpoint().equals(endpoint.id()))
                        .map(SnapshotCouplerAnimation.Parameters::position).findFirst().orElse(null);
                if (position == null) for (var candidate : entity.getContraption().getBlocks().keySet()) {
                    if (!HexieBridges.coupling().vehicleCouplingCatalog().endpointId(carriage, candidate).equals(endpoint.id())) continue;
                    position = candidate.immutable();
                    break;
                }
                if (position == null) return null;
                couplingPositions.put(endpoint.id(), position);
            }
            var info = entity.getContraption().getBlocks().get(position);
            return info != null && info.state().getBlock() == block ? info : null;
        }
        return null;
    }

    public Map<UUID, AssembledVehicleMotion.Source> sourcesFor(VehicleRuntimeView selected) {
        var result = new LinkedHashMap<UUID, AssembledVehicleMotion.Source>();
        for (var car : selected.definitions()) result.put(car.id(), vehicleMotion.sources.get(car.id()));
        return result;
    }

    /** Join only the two selected consists' route calculations; each carriage keeps its own source and cargo. */
    public AssembledVehicleMotion beginCoupling(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint, double speed) {
        return joinCouplingDomain(firstCarriage, firstEndpoint, other, secondCarriage, secondEndpoint, speed, null);
    }

    /** Share route coordinates for an approaching contact, without shunting or connecting either consist. */
    public AssembledVehicleMotion prepareContact(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint) {
        return prepareContact(firstCarriage, firstEndpoint, other, secondCarriage, secondEndpoint, null);
    }

    public AssembledVehicleMotion prepareContact(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint, Set<UUID> contactMembers) {
        return joinCouplingDomain(firstCarriage, firstEndpoint, other, secondCarriage, secondEndpoint, null, contactMembers);
    }

    public AssembledVehicleMotion joinCouplingDomain(UUID firstCarriage, UUID firstEndpoint,
            AssembledVehicleMotion other, UUID secondCarriage, UUID secondEndpoint, Double approachSpeed,
            Set<UUID> contactMembers) {
        if (other == null || vehicleMotion.level != other.level || !vehicleMotion.owns(firstCarriage) || !other.owns(secondCarriage))
            throw new IllegalArgumentException("Coupling requires two available carriages in this dimension");
        applyCouplingControlResets();
        other.applyCouplingControlResets();
        var firstHook = vehicleMotion.runtime.definition(firstCarriage).couplings().stream()
                .filter(e -> e.id().equals(firstEndpoint)).findFirst().orElse(null);
        var secondHook = other.runtime.definition(secondCarriage).couplings().stream()
                .filter(e -> e.id().equals(secondEndpoint)).findFirst().orElse(null);
        if (firstHook == null || secondHook == null)
            throw new IllegalArgumentException("Coupler does not belong to the selected carriage");
        if (!firstHook.matches(secondHook))
            throw new IllegalArgumentException("车钩型号不匹配：" + firstHook.effectiveModel() + " / " + secondHook.effectiveModel());
        if (!couplerAvailable(firstHook) || !other.couplerAvailable(secondHook))
            throw new IllegalStateException("车钩尚未完全伸出");
        if (other == vehicleMotion) {
            if (approachSpeed != null) vehicleMotion.runtime.beginCoupling(firstEndpoint, secondEndpoint, approachSpeed);
            applyCouplingControlResets();
            return vehicleMotion;
        }
        var firstIds = contactMembers == null ? Set.copyOf(vehicleMotion.runtime.state(firstCarriage).consist())
                : vehicleMotion.sources.keySet().stream().filter(contactMembers::contains).collect(java.util.stream.Collectors.toSet());
        var secondIds = contactMembers == null ? Set.copyOf(other.runtime.state(secondCarriage).consist())
                : other.sources.keySet().stream().filter(contactMembers::contains).collect(java.util.stream.Collectors.toSet());
        requireNoApproach(firstIds); other.requireNoApproach(secondIds);
        var firstState = vehicleMotion.runtime.subset(firstIds).checkpoint();
        var secondState = other.runtime.subset(secondIds).checkpoint();
        double[] firstRange = couplingRange(firstState), secondRange = couplingRange(secondState);
        var routeMerge = vehicleMotion.route.connectForCoupling(other.route, firstRange[0], firstRange[1], secondRange[0], secondRange[1]);
        var combined = HexieBridges.vehicle().vehicleRuntime().merge(routeMerge.route().path(),
                firstState, routeMerge.firstOffset(), secondState, routeMerge.secondOffset(), routeMerge.reverseSecond());
        // This validation is detached. An invalid route or endpoint cannot consume either live vehicle.
        if (approachSpeed != null) combined.beginCoupling(firstEndpoint, secondEndpoint, approachSpeed);
        var origins = new LinkedHashMap<UUID, AssembledVehicleMotion.Source>();
        firstIds.forEach(id -> origins.put(id, vehicleMotion.sources.get(id)));
        secondIds.forEach(id -> {
            var source = other.sources.get(id);
            origins.put(id, routeMerge.reverseSecond()
                    ? new AssembledVehicleMotion.Source(source.assembly(), source.forward().getOpposite(), source.modelForward()) : source);
        });
        var joined = new AssembledVehicleMotion(vehicleMotion.level, UUID.randomUUID(), vehicleMotion.forward(firstCarriage),
                routeMerge.route(), combined, origins);
        copyMemberState(joined); other.copyMemberState(joined);
        var left = remainder(firstIds);
        var otherLeft = other.remainder(secondIds);
        vehicleMotion.ensureCompatibility(); other.ensureCompatibility();
        var previous = new ArrayList<>(vehicleMotion.compatibility.trains());
        previous.addAll(other.compatibility.trains());
        // The original native Carriage objects are handed over, including unloaded cargo/passengers.
        // Unselected carriages retain their old bridge, route, movement and source records.
        try {
            transferRemainder(left);
            other.transferRemainder(otherLeft);
            vehicleMotion.installOwnerState(joined);
            joined.ensureCompatibility(previous);
            joined.compatibility.inheritControl(previous);
            if (approachSpeed == null) {
                // Unlike a player shunting request, this handoff occurs before the logical tick.
                // installOwnerState normally suppresses another tick after a mid-tick transfer.
                joined.lastTick = Math.max(vehicleMotion.lastTick, other.lastTick);
                if (left != null) left.lastTick = vehicleMotion.lastTick;
                if (otherLeft != null) otherLeft.lastTick = other.lastTick;
            }
        } catch (RuntimeException failure) {
            // Retain original runtimes until the handoff finishes. Never restore from block
            // snapshots here: the original native cargo objects remain authoritative.
            try {
                joined.removeCompatibility();
                if (left != null) left.removeCompatibility();
                if (otherLeft != null) otherLeft.removeCompatibility();
                vehicleMotion.removeCompatibility(); other.removeCompatibility();
                vehicleMotion.installOwnerState(vehicleMotion); other.installOwnerState(other);
                vehicleMotion.ensureCompatibility(previous); other.ensureCompatibility(previous);
                vehicleMotion.compatibility.inheritControl(previous); other.compatibility.inheritControl(previous);
            } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
        joined.applyCouplingControlResets();
        return joined;
    }

    public void requireNoApproach(Set<UUID> selected) {
        var endpoints = new HashSet<UUID>();
        selected.forEach(id -> vehicleMotion.runtime.definition(id).couplings().forEach(e -> endpoints.add(e.id())));
        if (vehicleMotion.runtime.couplingStates().stream().anyMatch(state -> !state.connected()
                && (endpoints.contains(state.first()) || endpoints.contains(state.second()))))
            throw new IllegalStateException("A selected consist already has a coupling operation");
    }

    public static double[] couplingRange(VehicleRuntimeCheckpointView state) {
        var distances = new java.util.DoubleSummaryStatistics();
        state.carriages().forEach(car -> distances.accept(car.distance()));
        state.motionDistances().values().forEach(distances::accept);
        state.placement().bogeys().values().forEach(bogey -> distances.accept(bogey.distance()));
        return new double[] {distances.getMin(), distances.getMax()};
    }

    public AssembledVehicleMotion remainder(Set<UUID> removed) {
        var remaining = new HashSet<>(vehicleMotion.sources.keySet());
        remaining.removeAll(removed);
        if (remaining.isEmpty()) return null;
        var selected = vehicleMotion.runtime.subset(remaining);
        var result = new AssembledVehicleMotion(vehicleMotion.level, vehicleMotion.assembly, vehicleMotion.forward, vehicleMotion.route, selected, sourcesFor(selected));
        copyMemberState(result);
        return result;
    }

    public void copyMemberState(AssembledVehicleMotion target) {
        vehicleMotion.cabControl.controllers.forEach((id, controller) -> { if (target.owns(id.carriage())) target.cabControl.controllers.put(id, controller); });
        vehicleMotion.cabControl.controlledCarriages.stream().filter(target::owns).forEach(target.cabControl.controlledCarriages::add);
        vehicleMotion.derailedCarriages.stream().filter(target::owns).forEach(target.derailedCarriages::add);
    }

    public void transferRemainder(AssembledVehicleMotion remaining) {
        var bridge = vehicleMotion.compatibility;
        vehicleMotion.compatibility = null;
        if (remaining != null) vehicleMotion.installOwnerState(remaining);
        var retained = bridge.retainFor(remaining);
        if (remaining != null) remaining.compatibility = retained;
    }

    /** Consume successful mechanical connections, never a route-domain merge or an approaching contact. */
    public void applyCouplingControlResets() {
        for (var reset : vehicleMotion.runtime.drainCouplingControlResets()) {
            var members = Set.copyOf(reset.carriages());
            double brake = reset.brake();
            var brakes = new BrakeState(brake, false, false, false, false);
            boolean matchedCabBrake = false;
            for (var entry : vehicleMotion.cabControl.controllers.entrySet()) {
                if (!members.contains(entry.getKey().carriage()) || entry.getValue().cabControls == null) continue;
                var state = entry.getValue().cabControls.state();
                if (state.input().brake() > brake || state.input().brake() == brake && !matchedCabBrake) {
                    brake = state.input().brake();
                    brakes = state.brakes();
                    matchedCabBrake = true;
                } else if (state.input().brake() == brake) {
                    var other = state.brakes();
                    brakes = new BrakeState(
                            Math.max(brakes.serviceBrake(), other.serviceBrake()),
                            brakes.emergencyHandle() || other.emergencyHandle(),
                            brakes.emergencyLatched() || other.emergencyLatched(),
                            brakes.parking() || other.parking(), false);
                }
            }
            for (var entry : vehicleMotion.cabControl.controllers.entrySet()) {
                if (!members.contains(entry.getKey().carriage())) continue;
                var driver = entry.getValue();
                if (driver.cabControls != null) {
                    var previous = driver.cabControls.state();
                    var resetState = previous.withDemands(new Input(0, brake, 0), brakes).withApproach(false);
                    driver.cabControls.submit(AssembledVehicleCabControl.synchronizeCabState(driver, previous, resetState));
                    driver.cabControls.keyboard(0);
                    vehicleMotion.releaseCabApproach(entry.getKey().carriage(), driver);
                } else if (driver.nativeRequest != null) {
                    driver.nativeRequest = new TrainCommand(0, brake);
                    driver.approachingStation = false;
                }
            }
            // A new compatibility train may not yet exist; the mechanical output is reset immediately regardless.
            for (UUID member : members) {
                vehicleMotion.runtime.controls(member, new TrainCommand(0, brake));
                var train = vehicleMotion.compatibility == null ? null : vehicleMotion.compatibility.train(member);
                if (train != null) {
                    train.stationState.backendAutomaticRequest = null;
                    train.manualSteer = SteerDirection.NONE;
                }
            }
            if (!members.isEmpty()) CreateSharedCabTimetables.afterCoupling(vehicleMotion, reset.carriages().getFirst());
        }
    }
}
