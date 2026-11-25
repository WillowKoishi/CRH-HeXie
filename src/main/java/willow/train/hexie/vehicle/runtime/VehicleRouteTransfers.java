package willow.train.hexie.vehicle.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.railway.TrackPathView;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.core.api.traincontrol.TrainCommand;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.Solution;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Approach;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Car;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Checkpoint;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Connection;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.CouplingControlReset;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.SavedCarriage;

// split from willow/train/hexie/vehicle/runtime/VehicleRuntime.java
final class VehicleRouteTransfers {
    private final VehicleRuntime runtime;

    VehicleRouteTransfers(VehicleRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Replace an extended route whose existing geometry is unchanged. Only the route coordinate
     * origin moves: preserve the exact solved body/bogey/endpoint poses and ongoing controls.
     * Restore validates the new path at every support before this replacement can be installed.
     */
    VehicleRuntime rebind(TrackPathView target, double distanceOffset) {
        VehicleRuntime.finite(distanceOffset, "route coordinate offset");
        var shiftedCars = runtime.cars.values().stream().map(car -> new SavedCarriage(car.definition,
                car.distance + distanceOffset, car.speed, car.controls)).toList();
        var shiftedBogeys = new LinkedHashMap<UUID, VehicleConstraintSolver.BogeyPosition>();
        runtime.placement.bogeyPositions().forEach((id, p) -> shiftedBogeys.put(id, new VehicleConstraintSolver.BogeyPosition(
                p.distance() + distanceOffset, p.lateral(), p.vertical(), p.track(), p.pivot(), p.attachment(),
                p.position(), p.suspensionLateral(), p.offRailOffset())));
        var rebound = VehicleRuntime.restore(new Checkpoint(shiftedCars, runtime.connections, runtime.approaches,
                new Solution(runtime.placement.bodyPoses(), shiftedBogeys, runtime.placement.endpointPoses()), runtime.bogeySpeeds,
                mapDistances(runtime.motionDistances, distance -> distance + distanceOffset)), target);
        rebound.dynamics.collisionEnabled = runtime.dynamics.collisionEnabled;
        rebound.dynamicsMultipliers(runtime.dynamics.accelerationMultiplier, runtime.dynamics.brakingMultiplier);
        rebound.consistRevision = runtime.consistRevision;
        copyCouplingControlResets(rebound);
        return rebound;
    }

    /** One-time backend geometry upgrade, mapping each saved support by its original edge parameter. */
    VehicleRuntime remapRoute(TrackPathView target, java.util.function.DoubleUnaryOperator mapDistance) {
        Objects.requireNonNull(mapDistance);
        var updated = VehicleRuntime.restoreSavedState(runtime.checkpoint(), target);
        var proposed = new LinkedHashMap<UUID, Double>();
        runtime.placement.bogeyPositions().forEach((id, support) -> {
            double mapped = mapDistance.applyAsDouble(support.distance());
            target.sample(mapped);
            proposed.put(id, mapped);
        });
        // A previous backend may have sampled a secondary curve that differs from the visible
        // primary. Re-solve the same links on the corrected geometry, retaining speeds/controls.
        updated.commit(updated.solver.solve(updated.definitions(), updated.connections,
                proposed, updated.pins(proposed), null));
        updated.motionDistances.putAll(mapDistances(runtime.motionDistances, mapDistance));
        updated.dynamics.collisionEnabled = runtime.dynamics.collisionEnabled;
        updated.dynamicsMultipliers(runtime.dynamics.accelerationMultiplier, runtime.dynamics.brakingMultiplier);
        updated.consistRevision += runtime.consistRevision;
        copyCouplingControlResets(updated);
        return updated;
    }

    /**
     * Combine two already aligned route domains without moving their world geometry or connecting
     * them automatically. The target keeps the first domain's direction. A reversed second domain
     * changes its normalized local frame by a half turn; adapters must likewise reverse each
     * affected carriage's captured-model basis. Source checkpoints remain untouched.
     */
    static VehicleRuntime merge(TrackPathView target, Checkpoint first, double firstOffset,
                                        Checkpoint second, double secondOffset, boolean reverseSecond) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        VehicleRuntime.finite(firstOffset, "first route offset");
        VehicleRuntime.finite(secondOffset, "second route offset");
        // Validate ownership, controls, links and diagnostic coordinates before transforming them.
        // This stage deliberately does not query either old route, which may be a shorter binding.
        VehicleRuntime.restoreSavedState(first, target);
        VehicleRuntime.restoreSavedState(second, target);
        Checkpoint a = remapCheckpoint(first, firstOffset, false);
        Checkpoint b = remapCheckpoint(second, secondOffset, reverseSecond);
        var carriages = new ArrayList<>(a.carriages());
        carriages.addAll(b.carriages());
        var links = new ArrayList<>(a.connections());
        links.addAll(b.connections());
        var pending = new ArrayList<>(a.approaches());
        pending.addAll(b.approaches());
        var bodies = new LinkedHashMap<>(a.placement().bodyPoses()); bodies.putAll(b.placement().bodyPoses());
        var bogeys = new LinkedHashMap<>(a.placement().bogeyPositions()); bogeys.putAll(b.placement().bogeyPositions());
        var endpoints = new LinkedHashMap<>(a.placement().endpointPoses()); endpoints.putAll(b.placement().endpointPoses());
        var speeds = new LinkedHashMap<>(a.bogeySpeeds()); speeds.putAll(b.bogeySpeeds());
        var cursors = new LinkedHashMap<>(a.motionDistances()); cursors.putAll(b.motionDistances());
        // Strict restore checks every remapped rail frame and re-solves the existing constraints.
        // An incorrect offset/direction or duplicate identity fails before either source is changed.
        return VehicleRuntime.restore(new Checkpoint(carriages, links, pending, new Solution(bodies, bogeys, endpoints), speeds, cursors), target);
    }

    private static Checkpoint remapCheckpoint(Checkpoint source, double offset, boolean reverse) {
        double sign = reverse ? -1 : 1;
        var carriages = new ArrayList<SavedCarriage>();
        for (SavedCarriage saved : source.carriages()) {
            CarriageDefinition definition = saved.definition();
            if (reverse) {
                var bogeys = definition.bogeys().stream().map(b -> new BogeyDefinition(b.id(), b.modelId(), -b.offset(),
                        b.geometry(), b.parameters(), new BogeyMount(halfTurn(b.mount().bodyPoint()),
                                halfTurn(b.mount().trackPivot()), b.mount().type(), b.mount().lateralLimit()))).toList();
                var endpoints = definition.couplings().stream().map(e -> new CouplingEndpoint(e.id(),
                        halfTurn(e.blockCenter()), switch (e.facing()) {
                            case NORTH -> CouplingBridge.CouplingEndpointFacing.SOUTH;
                            case EAST -> CouplingBridge.CouplingEndpointFacing.WEST;
                            case SOUTH -> CouplingBridge.CouplingEndpointFacing.NORTH;
                            case WEST -> CouplingBridge.CouplingEndpointFacing.EAST;
                        }, e.axis(), e.start(), e.complete(), e.kind(), e.maxExtension(), e.model(), e.adapter(), e.visual())).toList();
                definition = new CarriageDefinition(definition.id(), bogeys, endpoints,
                        definition.frontExtent(), definition.rearExtent(), definition.floorOffset());
            }
            var controls = new LinkedHashMap<UUID, TrainCommand>();
            saved.controls().forEach((id, control) -> controls.put(id, reverse
                    ? new TrainCommand(-control.throttle(), control.brake()) : control));
            carriages.add(new SavedCarriage(definition, sign * saved.distance() + offset, sign * saved.speed(), controls));
        }
        var bodies = new LinkedHashMap<UUID, VehicleConstraintSolver.BodyPose>();
        source.placement().bodyPoses().forEach((id, pose) -> bodies.put(id, reverse
                ? new VehicleConstraintSolver.BodyPose(pose.position(), pose.yaw() + Math.PI, -pose.pitch(), -pose.roll())
                : pose));
        var bogeys = new LinkedHashMap<UUID, VehicleConstraintSolver.BogeyPosition>();
        source.placement().bogeyPositions().forEach((id, support) -> {
            var sample = support.track();
            if (reverse) sample = TrackGeometryView.Sample.restoreNormalized(sample.position(), sample.tangent().scale(-1),
                    sample.normal(), -sample.cantRadians());
            bogeys.put(id, new VehicleConstraintSolver.BogeyPosition(sign * support.distance() + offset,
                    sign * support.lateral(), support.vertical(), sample, support.pivot(), support.attachment(),
                    support.position(), sign * support.suspensionLateral(), support.offRailOffset()));
        });
        var speeds = new LinkedHashMap<UUID, Double>();
        source.bogeySpeeds().forEach((id, speed) -> speeds.put(id, sign * speed));
        return new Checkpoint(carriages, source.connections(), source.approaches(),
                new Solution(bodies, bogeys, source.placement().endpointPoses()), speeds,
                mapDistances(source.motionDistances(), distance -> sign * distance + offset), source.legacyCouplerAxes());
    }

    private static Point3 halfTurn(Point3 point) {
        return new Point3(-point.x(), point.y(), -point.z());
    }

    /**
     * Rebuild this whole route domain against explicitly mapped carriage reference distances.
     * No state is changed on this runtime if validation/placement fails. Definitions, hard links
     * and pending approach requests survive; approach progress follows the new endpoint geometry.
     */
    VehicleRuntime rebase(TrackPathView target, Map<UUID, Double> carriageDistances, boolean stop) {
        return rebase(target, carriageDistances, stop ? 0.0 : null, stop);
    }

    /** Reposition with one explicit velocity for the whole domain; clear prior driver commands. */
    VehicleRuntime rebase(TrackPathView target, Map<UUID, Double> carriageDistances, double speed) {
        VehicleRuntime.finite(speed, "relocation speed");
        return rebase(target, carriageDistances, speed, true);
    }

    /** Recover complete consists on a new route even if the saved route is unavailable. */
    static VehicleRuntime relocateCheckpoint(Checkpoint source, TrackPathView target,
                                                     Map<UUID, Double> carriageDistances) {
        Objects.requireNonNull(carriageDistances);
        // Validate the complete saved domain before selecting it, without querying its old track.
        VehicleRuntime.restoreSavedState(source, target);
        var selected = VehicleRuntime.subsetCheckpoint(source, carriageDistances.keySet());
        return VehicleRuntime.restoreSavedState(selected, target).rebase(target, carriageDistances, true);
    }

    private VehicleRuntime rebase(TrackPathView target, Map<UUID, Double> carriageDistances,
                                  Double speedOverride, boolean clearControls) {
        Objects.requireNonNull(carriageDistances);
        if (!carriageDistances.keySet().equals(runtime.cars.keySet()))
            throw new IllegalArgumentException("Rebase requires a distance for every carriage in the route domain");
        var moved = new VehicleRuntime(target);
        var proposed = new LinkedHashMap<UUID, Double>();
        for (Car car : runtime.cars.values()) {
            double distance = Objects.requireNonNull(carriageDistances.get(car.definition.id()));
            double speed = speedOverride == null ? car.speed : speedOverride;
            moved.installCarriage(car.definition, distance, speed);
            if (!clearControls) moved.car(car.definition.id()).controls.putAll(car.controls);
            for (BogeyDefinition bogey : car.definition.bogeys()) {
                double cursor = distance + bogey.offset() + bogey.mount().trackPivot().z();
                // Validate every registered support before solving; endpoints are not the whole body.
                target.sample(cursor);
                proposed.put(bogey.id(), cursor);
                moved.bogeySpeeds.put(bogey.id(), speedOverride == null ? runtime.bogeySpeeds.get(bogey.id()) : speed);
            }
        }
        for (Connection connection : runtime.connections) {
            moved.validatePair(connection.first(), connection.second());
            moved.connections.add(connection);
        }
        // A cold solve avoids carrying a distant old-world pose into the new track's local branch.
        moved.commit(moved.solver.solve(moved.definitions(), moved.connections, proposed, moved.pins(proposed), null));
        for (Approach approach : runtime.approaches) {
            moved.validatePair(approach.first(), approach.second());
            moved.validateApproachParticipants(approach.first(), approach.second());
            moved.approaches.add(approach);
        }
        moved.dynamics.collisionEnabled = runtime.dynamics.collisionEnabled;
        moved.dynamicsMultipliers(runtime.dynamics.accelerationMultiplier, runtime.dynamics.brakingMultiplier);
        moved.consistRevision += runtime.consistRevision;
        copyCouplingControlResets(moved);
        return moved;
    }

    /**
     * Copy whole connected components for a backend/domain split. A hard link may not be cut.
     * Manual approaches crossing this boundary are cancelled in the copy, never converted to links.
     * The caller retains/replaces the complementary domain explicitly; this method removes no cars.
     */
    VehicleRuntime subset(Set<UUID> carriageIds) {
        var copied = VehicleRuntime.restore(VehicleRuntime.subsetCheckpoint(runtime.checkpoint(), carriageIds), runtime.path);
        copied.dynamics.collisionEnabled = runtime.dynamics.collisionEnabled;
        copied.dynamicsMultipliers(runtime.dynamics.accelerationMultiplier, runtime.dynamics.brakingMultiplier);
        copied.consistRevision = runtime.consistRevision;
        copyCouplingControlResets(copied);
        return copied;
    }

    /**
     * Retain complete live consists without resampling their track. Removing other consists
     * changes no solved pose; the external route may be unavailable until the next rebind.
     * Saved-state restoration still checks ownership, connections, controls and cursors.
     */
    VehicleRuntime subsetPreservingPlacement(Set<UUID> carriageIds) {
        var copied = VehicleRuntime.restoreSavedState(VehicleRuntime.subsetCheckpoint(runtime.checkpoint(), carriageIds), runtime.path);
        copied.dynamics.collisionEnabled = runtime.dynamics.collisionEnabled;
        copied.dynamicsMultipliers(runtime.dynamics.accelerationMultiplier, runtime.dynamics.brakingMultiplier);
        copied.consistRevision = runtime.consistRevision;
        copyCouplingControlResets(copied);
        return copied;
    }

    private void copyCouplingControlResets(VehicleRuntime target) {
        for (var reset : runtime.couplingControlResets) {
            var retained = reset.carriages().stream().filter(target.cars::containsKey).toList();
            if (!retained.isEmpty()) target.couplingControlResets.add(new CouplingControlReset(retained, reset.brake()));
        }
    }

    private static Map<UUID, Double> mapDistances(Map<UUID, Double> source, java.util.function.DoubleUnaryOperator mapper) {
        var result = new LinkedHashMap<UUID, Double>();
        source.forEach((id, value) -> result.put(id, mapper.applyAsDouble(value)));
        return result;
    }
}
