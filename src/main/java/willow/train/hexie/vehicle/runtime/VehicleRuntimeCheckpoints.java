package willow.train.hexie.vehicle.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.railway.TrackPathView;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.Solution;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Approach;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Car;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Checkpoint;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Connection;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.RouteMismatchException;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.SavedCarriage;

// split from willow/train/hexie/vehicle/runtime/VehicleRuntime.java
final class VehicleRuntimeCheckpoints {
    private final VehicleRuntime runtime;

    VehicleRuntimeCheckpoints(VehicleRuntime runtime) {
        this.runtime = runtime;
    }

    Checkpoint checkpoint() {
        return new Checkpoint(runtime.cars.values().stream().map(c -> new SavedCarriage(c.definition,
                c.distance, c.speed, c.controls)).toList(), runtime.connections, runtime.approaches, runtime.placement, runtime.bogeySpeeds, runtime.motionDistances);
    }

    /** Select saved complete consists without resolving or resampling their previous route. */
    static Checkpoint subsetCheckpoint(Checkpoint source, Set<UUID> carriageIds) {
        Set<UUID> selected = Set.copyOf(carriageIds);
        var carIds = new HashSet<UUID>();
        var endpointOwners = new HashMap<UUID, UUID>();
        for (SavedCarriage saved : source.carriages()) {
            if (!carIds.add(saved.definition().id()))
                throw new IllegalArgumentException("Duplicate checkpoint carriage");
            for (CouplingEndpoint endpoint : saved.definition().couplings())
                if (endpointOwners.putIfAbsent(endpoint.id(), saved.definition().id()) != null)
                    throw new IllegalArgumentException("Duplicate checkpoint endpoint");
        }
        if (!carIds.containsAll(selected)) throw new IllegalArgumentException("Unknown subset carriage");
        for (Connection connection : source.connections()) {
            UUID first = Objects.requireNonNull(endpointOwners.get(connection.first()), "Unknown connected endpoint");
            UUID second = Objects.requireNonNull(endpointOwners.get(connection.second()), "Unknown connected endpoint");
            if (selected.contains(first) != selected.contains(second))
                throw new IllegalArgumentException("Subset cannot cut a connected consist");
        }
        for (Approach approach : source.approaches()) {
            Objects.requireNonNull(endpointOwners.get(approach.first()), "Unknown approach endpoint");
            Objects.requireNonNull(endpointOwners.get(approach.second()), "Unknown approach endpoint");
        }
        var selectedCars = source.carriages().stream().filter(car -> selected.contains(car.definition().id())).toList();
        var selectedConnections = source.connections().stream().filter(c -> selected.contains(endpointOwners.get(c.first()))).toList();
        var selectedApproaches = source.approaches().stream().filter(a -> selected.contains(endpointOwners.get(a.first()))
                && selected.contains(endpointOwners.get(a.second()))).toList();
        var bodies = new LinkedHashMap<UUID, VehicleConstraintSolver.BodyPose>();
        var bogeys = new LinkedHashMap<UUID, VehicleConstraintSolver.BogeyPosition>();
        var endpoints = new LinkedHashMap<UUID, VehicleConstraintSolver.EndpointPose>();
        var speeds = new LinkedHashMap<UUID, Double>();
        var cursors = new LinkedHashMap<UUID, Double>();
        for (SavedCarriage car : selectedCars) {
            bodies.put(car.definition().id(), source.placement().bodyPoses().get(car.definition().id()));
            for (BogeyDefinition bogey : car.definition().bogeys()) {
                bogeys.put(bogey.id(), source.placement().bogeyPositions().get(bogey.id()));
                speeds.put(bogey.id(), source.bogeySpeeds().get(bogey.id()));
                cursors.put(bogey.id(), source.motionDistances().get(bogey.id()));
            }
            for (CouplingEndpoint endpoint : car.definition().couplings())
                endpoints.put(endpoint.id(), source.placement().endpointPoses().get(endpoint.id()));
        }
        return new Checkpoint(selectedCars, selectedConnections, selectedApproaches,
                new Solution(bodies, bogeys, endpoints), speeds, cursors, source.legacyCouplerAxes());
    }

    static VehicleRuntime restore(Checkpoint checkpoint) {
        return restore(checkpoint, TrackPathView.straight());
    }

    static VehicleRuntime restore(Checkpoint checkpoint, TrackPathView path) {
        return restore(checkpoint, path, checkpoint.legacyCouplerAxes());
    }

    /** One-time upgrade of snapshots written before rigid end-support constraints were enforced. */
    static VehicleRuntime restoreLegacyPlacement(Checkpoint checkpoint, TrackPathView path) {
        return restore(checkpoint, path, true);
    }

    static VehicleRuntime restore(Checkpoint checkpoint, TrackPathView path, boolean upgradePlacement) {
        var runtime = restoreSavedState(checkpoint, path);
        var savedDistances = VehicleRuntime.distances(runtime.placement);
        // Distinguish an edited/unavailable external route from malformed carriage/control NBT.
        // The adapter can retain the saved vehicle while waiting for its track backend.
        try {
            for (var saved : runtime.placement.bogeyPositions().values()) {
                var current = path.sample(saved.distance());
                close(saved.track().position(), current.position());
                close(saved.track().tangent(), current.tangent());
                close(saved.track().normal(), current.normal());
                close(saved.track().cantRadians(), current.cantRadians());
            }
        } catch (RuntimeException failure) { throw new RouteMismatchException(failure); }
        var validated = runtime.solver.solve(runtime.definitions(), runtime.connections, savedDistances,
                runtime.pins(savedDistances), runtime.placement);
        if (checkpoint.legacyCouplerAxes() && !validated.brokenConnections().isEmpty())
            throw new IllegalArgumentException("Legacy coupling cannot be restored on its vertical axes");
        if (upgradePlacement) runtime.commit(validated);
        else requireSamePlacement(runtime.placement, validated);
        return runtime;
    }

    /** Restore and validate saved data independently from availability of the old track geometry. */
    static VehicleRuntime restoreSavedState(Checkpoint checkpoint, TrackPathView path) {
        var runtime = new VehicleRuntime(path);
        for (SavedCarriage saved : checkpoint.carriages()) {
            runtime.installCarriage(saved.definition(), saved.distance(), saved.speed());
            Car car = runtime.car(saved.definition().id());
            if (!saved.controls().keySet().equals(car.controls.keySet()))
                throw new IllegalArgumentException("Checkpoint bogey controls do not match its carriage");
            saved.controls().forEach((id, control) -> car.controls.put(id, Objects.requireNonNull(control)));
        }
        runtime.placement = checkpoint.placement();
        if (!runtime.placement.bodyPoses().keySet().equals(runtime.cars.keySet())
                || !runtime.placement.bogeyPositions().keySet().equals(runtime.bogeyOwners.keySet())
                || !runtime.placement.endpointPoses().keySet().equals(runtime.endpointOwners.keySet())
                || !checkpoint.bogeySpeeds().keySet().equals(runtime.bogeyOwners.keySet())
                || !checkpoint.motionDistances().keySet().equals(runtime.bogeyOwners.keySet()))
            throw new IllegalArgumentException("Checkpoint placement does not match its definitions");
        checkpoint.bogeySpeeds().forEach((id, speed) -> { VehicleRuntime.finite(speed, "bogey speed"); runtime.bogeySpeeds.put(id, speed); });
        checkpoint.motionDistances().forEach((id, distance) -> {
            VehicleRuntime.finite(distance, "motion distance"); runtime.motionDistances.put(id, distance);
        });
        for (Connection connection : checkpoint.connections()) {
            double firstSpeed = runtime.car(runtime.owner(connection.first())).speed;
            double secondSpeed = runtime.car(runtime.owner(connection.second())).speed;
            if (Math.abs(firstSpeed - secondSpeed) > VehicleRuntime.EPSILON)
                throw new IllegalArgumentException("Connected checkpoint carriages have different velocities");
            runtime.validatePair(connection.first(), connection.second());
            if (VehicleRuntime.endpointGap(runtime.placement, connection.first(), connection.second())
                    > runtime.endpoint(connection.first()).maxExtension() + runtime.endpoint(connection.second()).maxExtension()
                    + VehicleRuntime.PLACEMENT_TOLERANCE)
                throw new IllegalArgumentException("Checkpoint connected endpoints exceed their extension range");
            runtime.connections.add(connection);
        }
        for (Car car : runtime.cars.values()) {
            var reference = car.definition.bogeys().getFirst();
            close(car.distance, runtime.placement.bogeyPositions().get(reference.id()).distance()
                    - reference.offset() - reference.mount().trackPivot().z());
        }
        // Keep the exact saved state after verification; loading must not move the carriage.
        for (Approach approach : checkpoint.approaches()) {
            runtime.validatePair(approach.first(), approach.second());
            runtime.validateApproachParticipants(approach.first(), approach.second());
            // Restoring is not a new interaction: a near-complete request still finishes on its next tick.
            runtime.approaches.add(approach);
        }
        return runtime;
    }

    private static void requireSamePlacement(Solution expected, Solution actual) {
        requireSamePlacement(expected, actual, true);
    }

    private static void requireSamePlacement(Solution expected, Solution actual, boolean endpointsUnchanged) {
        if (!expected.bodyPoses().keySet().equals(actual.bodyPoses().keySet())
                || !expected.bogeyPositions().keySet().equals(actual.bogeyPositions().keySet())
                || !expected.endpointPoses().keySet().equals(actual.endpointPoses().keySet()))
            throw new IllegalArgumentException("Solved placement identities changed");
        expected.bogeyPositions().forEach((id, before) -> {
            var after = actual.bogeyPositions().get(id);
            close(before.distance(), after.distance()); close(before.lateral(), after.lateral());
            close(before.vertical(), after.vertical());
            close(before.pivot(), after.pivot()); close(before.attachment(), after.attachment());
            close(before.position(), after.position()); close(before.offRailOffset(), after.offRailOffset());
            close(before.suspensionLateral(), after.suspensionLateral());
            close(before.track().position(), after.track().position());
            close(before.track().tangent(), after.track().tangent());
            close(before.track().normal(), after.track().normal());
            close(before.track().cantRadians(), after.track().cantRadians());
        });
        expected.bodyPoses().forEach((id, before) -> {
            var after = actual.bodyPoses().get(id);
            close(before.position(), after.position());
            close(Math.IEEEremainder(before.yaw() - after.yaw(), Math.TAU), 0);
            close(Math.IEEEremainder(before.pitch() - after.pitch(), Math.TAU), 0);
            close(Math.IEEEremainder(before.roll() - after.roll(), Math.TAU), 0);
        });
        if (endpointsUnchanged)
            expected.endpointPoses().forEach((id, before) -> {
                var after = actual.endpointPoses().get(id);
                close(before.pivot(), after.pivot()); close(before.start(), after.start()); close(before.complete(), after.complete());
            });
    }

    private static void close(TrackGeometryView.Vector first, TrackGeometryView.Vector second) {
        var difference = first.subtract(second);
        close(Math.sqrt(difference.dot(difference)), 0);
    }

    private static void close(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second) || Math.abs(first - second) > VehicleRuntime.PLACEMENT_TOLERANCE)
            throw new IllegalArgumentException("Checkpoint or connection would displace the solved geometry");
    }
}
