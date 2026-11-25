package willow.train.hexie.vehicle.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.vehicle.CouplingEndpointView;
import willow.train.hexie.core.api.traincontrol.TrainCommand;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.Solution;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Approach;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Car;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Connection;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.CouplingControlReset;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.CouplingState;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Group;

// split from willow/train/hexie/vehicle/runtime/VehicleRuntime.java
final class VehicleRuntimeCouplings {
    private final VehicleRuntime runtime;

    VehicleRuntimeCouplings(VehicleRuntime runtime) {
        this.runtime = runtime;
    }

    private long indexedConsistRevision = -1;
    private final Map<UUID, UUID> couplingPartners = new HashMap<>();

    /** Upgrade legacy saved endpoints once; this changes only captured model metadata. */
    void captureCouplerVisual(UUID id, CouplingEndpoint.Visual visual) {
        Objects.requireNonNull(visual);
        var carriage = runtime.car(owner(id));
        var old = carriage.definition;
        var updated = old.couplings().stream().map(endpoint -> endpoint.id().equals(id) && endpoint.visual() == null
                ? new CouplingEndpoint(endpoint.id(), endpoint.blockCenter(), endpoint.facing(), endpoint.axis(),
                        endpoint.start(), endpoint.complete(), endpoint.kind(), endpoint.maxExtension(), endpoint.model(),
                        endpoint.adapter(), visual) : endpoint).toList();
        if (!updated.equals(old.couplings())) carriage.definition = new CarriageDefinition(old.id(), old.bogeys(), updated,
                old.rearExtent(), old.frontExtent(), old.floorOffset());
    }
    /** Explicit appearance edits preserve coupling geometry, adapters, connections and solved poses. */
    boolean updateCouplerVisual(UUID id, CouplingEndpoint.Visual visual) {
        Objects.requireNonNull(visual);
        var carriage = runtime.car(owner(id));
        var old = carriage.definition;
        var updated = old.couplings().stream().map(endpoint -> endpoint.id().equals(id)
                ? new CouplingEndpoint(endpoint.id(), endpoint.blockCenter(), endpoint.facing(), endpoint.axis(),
                        endpoint.start(), endpoint.complete(), endpoint.kind(), endpoint.maxExtension(), endpoint.model(),
                        endpoint.adapter(), visual) : endpoint).toList();
        if (updated.equals(old.couplings())) return false;
        carriage.definition = new CarriageDefinition(old.id(), old.bogeys(), updated,
                old.rearExtent(), old.frontExtent(), old.floorOffset());
        return true;
    }

    /** Constant-time per-hook animation lookup, rebuilt only when independent topology changes. */
    UUID couplingPartner(UUID endpoint) {
        if (indexedConsistRevision != runtime.consistRevision) {
            couplingPartners.clear();
            for (Connection connection : runtime.connections) {
                couplingPartners.put(connection.first(), connection.second());
                couplingPartners.put(connection.second(), connection.first());
            }
            indexedConsistRevision = runtime.consistRevision;
        }
        return couplingPartners.get(endpoint);
    }

    boolean connected(UUID first, UUID second) { return runtime.group(first).contains(second); }

    /** The owning adapter consumes these events to update its physical driving desks. */
    List<CouplingControlReset> drainCouplingControlResets() {
        if (runtime.couplingControlResets.isEmpty()) return List.of();
        var pending = List.copyOf(runtime.couplingControlResets);
        runtime.couplingControlResets.clear();
        return pending;
    }

    private java.util.function.Predicate<CouplingEndpointView> couplingAvailability = ignored -> true;
    void couplingAvailability(java.util.function.Predicate<CouplingEndpointView> available) {
        couplingAvailability = java.util.Objects.requireNonNull(available);
    }
    boolean couplingAvailable(CouplingEndpoint endpoint) { return couplingAvailability.test(endpoint); }
    private void requireAvailable(UUID first, UUID second) {
        if (!couplingAvailable(endpoint(first)) || !couplingAvailable(endpoint(second)))
            throw new IllegalStateException("车钩尚未完全伸出");
    }

    /** The registered completion regions must meet before the connected constraint is installed. */
    void couple(UUID first, UUID second) {
        validatePair(first, second);
        requireAvailable(first, second);
        if (!regionsMeet(runtime.placement, first, second, false))
            throw new IllegalStateException("Coupling completion regions do not overlap");
        Group a = runtime.group(owner(first)), b = runtime.group(owner(second));
        double velocity = VehicleRuntime.momentumVelocity(a, b);
        runtime.connections.add(new Connection(first, second));
        Solution connected;
        try {
            var current = VehicleRuntime.distances(runtime.placement);
            connected = VehicleRuntime.overlay(runtime.placement, runtime.solveGroups(List.of(runtime.group(owner(first))), current));
            // Contact accepts the block regions; the connected solver then places the joint.
            // Requiring unchanged point poses here would reintroduce point-only contact.
        } catch (RuntimeException failure) {
            runtime.connections.removeLast();
            throw failure;
        }
        runtime.commit(connected);
        // A direct connection may use a different free endpoint of a manually moving consist.
        // Cancel those requests too, or a newly merged consist can acquire conflicting shunting controls.
        runtime.approaches.removeIf(p -> a.contains(owner(p.first())) || a.contains(owner(p.second()))
                || b.contains(owner(p.first())) || b.contains(owner(p.second())));
        a.velocity(velocity); b.velocity(velocity);
        var joined = new ArrayList<Car>(a.cars());
        joined.addAll(b.cars());
        double brake = joined.stream().flatMap(car -> car.controls.values().stream())
                .mapToDouble(TrainCommand::brake).max().orElse(0);
        var reset = new TrainCommand(0, brake);
        for (Car car : joined) car.controls.replaceAll((id, previous) -> reset);
        runtime.couplingControlResets.add(new CouplingControlReset(joined.stream().map(car -> car.definition.id()).toList(), brake));
        runtime.consistRevision++;
    }

    void uncouple(UUID endpoint) {
        endpoint(endpoint);
        // Releasing a link is a topology operation, never conditional on another pose solve.
        var released = runtime.connections.stream().filter(c -> c.first().equals(endpoint) || c.second().equals(endpoint)).toList();
        if (released.isEmpty()) return;
        runtime.connections.removeAll(released);
        var endpoints = new LinkedHashMap<>(runtime.placement.endpointPoses());
        for (Connection link : released) for (UUID id : List.of(link.first(), link.second())) {
            var definition = endpoint(id);
            var body = runtime.placement.bodyPoses().get(owner(id));
            endpoints.put(id, new VehicleConstraintSolver.EndpointPose(body.transform(definition.localPivot()),
                    body.transform(definition.localStart()), body.transform(definition.localComplete())));
        }
        runtime.placement = new Solution(runtime.placement.bodyPoses(), runtime.placement.bogeyPositions(), endpoints);
        runtime.consistRevision++;
    }

    /** Explicit shunting request: the first car and its consist approach the second endpoint. */
    void beginCoupling(UUID first, UUID second, double approachSpeedMps) {
        validatePair(first, second);
        requireAvailable(first, second);
        Approach approach = new Approach(first, second, approachSpeedMps);
        validateApproachParticipants(first, second);
        if (regionsMeet(runtime.placement, first, second, false)) couple(first, second);
        else runtime.approaches.add(approach);
    }

    void validateApproachParticipants(UUID first, UUID second) {
        Set<UUID> affected = new HashSet<>();
        runtime.group(owner(first)).cars().forEach(c -> affected.add(c.definition.id()));
        runtime.group(owner(second)).cars().forEach(c -> affected.add(c.definition.id()));
        if (runtime.approaches.stream().anyMatch(a -> affected.contains(owner(a.first())) || affected.contains(owner(a.second()))))
            throw new IllegalStateException("A consist already participates in manual coupling");
    }

    void cancelCoupling(UUID endpoint) {
        endpoint(endpoint);
        runtime.approaches.removeIf(a -> a.first().equals(endpoint) || a.second().equals(endpoint));
    }

    /** Reconcile persisted block-owned release conditions before the next motion step. */
    void releaseCouplingsIf(java.util.function.Predicate<CouplingEndpointView> shouldRelease) {
        Objects.requireNonNull(shouldRelease);
        if (runtime.connections.isEmpty() && runtime.approaches.isEmpty()) return;
        var released = new java.util.LinkedHashSet<UUID>();
        for (var connection : runtime.connections) {
            if (shouldRelease.test(endpoint(connection.first()))) released.add(connection.first());
            if (shouldRelease.test(endpoint(connection.second()))) released.add(connection.second());
        }
        for (var approach : runtime.approaches) {
            if (shouldRelease.test(endpoint(approach.first()))) released.add(approach.first());
            if (shouldRelease.test(endpoint(approach.second()))) released.add(approach.second());
        }
        for (UUID id : released) {
            uncouple(id);
            cancelCoupling(id);
        }
    }

    /** Install on a free hook without moving its body, bogeys or independent dynamics cursors. */
    void installCouplerAdapter(UUID endpointId, CouplerAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        CouplingEndpoint previous = endpoint(endpointId);
        replaceCouplerAdapter(endpointId, CouplerAdapter.append(previous.model(), previous.adapter(), adapter));
    }

    CouplerAdapter removeCouplerAdapter(UUID endpointId) {
        var previous = endpoint(endpointId);
        if (previous.adapter() == null) return null;
        var removed = previous.adapter().outer();
        replaceCouplerAdapter(endpointId, previous.adapter().withoutOuter(previous.model()));
        return removed;
    }

    private void replaceCouplerAdapter(UUID endpointId, CouplerAdapter adapter) {
        var previous = endpoint(endpointId);
        if (couplingPartner(endpointId) != null || runtime.approaches.stream().anyMatch(a ->
                a.first().equals(endpointId) || a.second().equals(endpointId)))
            throw new IllegalStateException("Release the coupling before changing adapters");
        CouplingEndpoint updated = previous.withAdapter(adapter);
        Car car = runtime.car(owner(endpointId));
        CarriageDefinition old = car.definition;
        car.definition = new CarriageDefinition(old.id(), old.bogeys(), old.couplings().stream()
                .map(e -> e.id().equals(endpointId) ? updated : e).toList(), old.rearExtent(), old.frontExtent(), old.floorOffset());
        var endpoints = new LinkedHashMap<>(runtime.placement.endpointPoses());
        var body = runtime.placement.bodyPoses().get(old.id());
        endpoints.put(endpointId, new VehicleConstraintSolver.EndpointPose(body.transform(updated.localPivot()),
                body.transform(updated.localStart()), body.transform(updated.localComplete())));
        runtime.placement = new Solution(runtime.placement.bodyPoses(), runtime.placement.bogeyPositions(), endpoints);
        runtime.consistRevision++;
    }

    List<CouplingState> couplingStates() {
        var result = new ArrayList<CouplingState>();
        for (Connection connection : runtime.connections)
            result.add(couplingState(connection.first(), connection.second(), 1, true));
        for (Approach approach : runtime.approaches) {
            double distance = Math.max(0, separation(runtime.placement, new Connection(approach.first(), approach.second()),
                    couplingDirection(runtime.placement, approach.first())));
            double stroke = endpoint(approach.first()).engagementLength() + endpoint(approach.second()).engagementLength();
            double progress = !regionsMeet(runtime.placement, approach.first(), approach.second(), true) ? 0
                    : stroke > VehicleRuntime.EPSILON ? Math.clamp(1 - distance / stroke, 0, 1) : distance <= VehicleRuntime.EPSILON ? 1 : 0;
            result.add(couplingState(approach.first(), approach.second(), progress, false));
        }
        return List.copyOf(result);
    }

    private CouplingState couplingState(UUID first, UUID second, double progress, boolean connected) {
        return new CouplingState(first, second, progress, connected, endpoint(first).kind(),
                connected ? endpointGap(runtime.placement, first, second) : 0,
                CouplerAngles.from(endpoint(first), runtime.placement.bodyPoses().get(owner(first)), runtime.placement.endpointPoses().get(first)),
                CouplerAngles.from(endpoint(second), runtime.placement.bodyPoses().get(owner(second)), runtime.placement.endpointPoses().get(second)));
    }

    TrackGeometryView.Vector couplingDirection(Solution solution, UUID id) {
        return solution.bodyPoses().get(owner(id)).rotate(endpoint(id).rotated(new Point3(0, 0, -1)));
    }

    boolean regionsMeet(Solution solution, UUID first, UUID second, boolean engagement) {
        CarriageDefinition a = runtime.car(owner(first)).definition, b = runtime.car(owner(second)).definition;
        return CouplingRegion.of(endpoint(first), solution.bodyPoses().get(a.id()), a.floorOffset(), engagement)
                .intersects(CouplingRegion.of(endpoint(second), solution.bodyPoses().get(b.id()), b.floorOffset(), engagement),
                        VehicleRuntime.PLACEMENT_TOLERANCE);
    }

    static double separation(Solution solution, Connection coupling, TrackGeometryView.Vector direction) {
        return solution.endpointPoses().get(coupling.second()).complete()
                .subtract(solution.endpointPoses().get(coupling.first()).complete()).dot(direction);
    }

    static double endpointGap(Solution solution, UUID first, UUID second) {
        var delta = solution.endpointPoses().get(first).complete().subtract(solution.endpointPoses().get(second).complete());
        return Math.sqrt(delta.dot(delta));
    }

    void validatePair(UUID first, UUID second) {
        CouplingEndpoint a = endpoint(first), b = endpoint(second);
        if (first.equals(second) || runtime.group(owner(first)).contains(owner(second)))
            throw new IllegalArgumentException("Coupling endpoints must belong to separate consists");
        if (runtime.connections.stream().anyMatch(c -> c.first().equals(first) || c.second().equals(first)
                || c.first().equals(second) || c.second().equals(second))) throw new IllegalStateException("Occupied coupling endpoint");
        if (a.kind() != CouplingBridge.CouplingEndpointKind.RIGID || b.kind() != CouplingBridge.CouplingEndpointKind.RIGID)
            throw new UnsupportedOperationException("Articulated constraints require their own animation/constraint adapter");
        if (!a.matches(b)) throw new IllegalArgumentException("Coupler models do not match");
        boolean opposing = a.facing() == CouplingBridge.CouplingEndpointFacing.NORTH && b.facing() == CouplingBridge.CouplingEndpointFacing.SOUTH
                || a.facing() == CouplingBridge.CouplingEndpointFacing.SOUTH && b.facing() == CouplingBridge.CouplingEndpointFacing.NORTH;
        if (!opposing)
            throw new IllegalArgumentException("Endpoints are not aligned in this route frame");
    }

    boolean manualPair(Group a, Group b) {
        return runtime.approaches.stream().anyMatch(p -> a.contains(owner(p.first())) && b.contains(owner(p.second()))
                || b.contains(owner(p.first())) && a.contains(owner(p.second())));
    }

    boolean involved(Approach approach, UUID car) {
        return runtime.group(owner(approach.first())).contains(car) || runtime.group(owner(approach.second())).contains(car);
    }

    UUID owner(UUID endpoint) {
        UUID id = runtime.endpointOwners.get(endpoint);
        if (id == null) throw new IllegalArgumentException("Unknown coupling endpoint " + endpoint);
        return id;
    }
    CouplingEndpoint endpoint(UUID id) {
        return runtime.car(owner(id)).definition.couplings().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }
}
