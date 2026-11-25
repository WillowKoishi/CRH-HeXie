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
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.core.api.bogey.BogeyParameters;
import willow.train.hexie.core.api.traincontrol.TrainCommand;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.Solution;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Approach;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Car;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Connection;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.ConstraintFailurePolicy;
import willow.train.hexie.vehicle.runtime.VehicleRuntime.Group;

// split from willow/train/hexie/vehicle/runtime/VehicleRuntime.java
final class VehicleRuntimeDynamics {
    private final VehicleRuntime runtime;

    VehicleRuntimeDynamics(VehicleRuntime runtime) {
        this.runtime = runtime;
    }

    private static final double MAX_STEP = 0.05;

    boolean collisionEnabled = true;
    double accelerationMultiplier = 1;
    double brakingMultiplier = 1;

    boolean collisionEnabled() { return collisionEnabled; }
    /** Backend-supplied policy; never changes saved bogey specifications. */
    void dynamicsMultipliers(double acceleration, double braking) {
        if (!Double.isFinite(acceleration) || acceleration <= 0
                || !Double.isFinite(braking) || braking <= 0)
            throw new IllegalArgumentException("Dynamics multipliers must be finite and positive");
        accelerationMultiplier = acceleration;
        brakingMultiplier = braking;
    }
    double brakingMultiplier() { return brakingMultiplier; }
    private Set<UUID> poweredCarriages = Set.of();
    private double linePowerMultiplier = 1;

    void poweredCarriages(Set<UUID> carriages, double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 1) throw new IllegalArgumentException("Invalid wheel power multiplier");
        poweredCarriages = Set.copyOf(carriages);
        linePowerMultiplier = multiplier;
    }

    double wheelPowerMultiplier(UUID carriage) {
        return poweredCarriages.contains(carriage) ? linePowerMultiplier : 1;
    }
    /** Backend policy: Create owns body impacts; manual and contact coupling remain active. */
    void collisionEnabled(boolean enabled) { collisionEnabled = enabled; }
    ConstraintFailurePolicy constraintFailurePolicy() { return ConstraintFailurePolicy.RETRY_CONSIST; }
    /** Legacy API; pose failure handling is identical with and without a compatibility backend. */
    void constraintFailurePolicy(ConstraintFailurePolicy policy) {
        Objects.requireNonNull(policy);
    }

    /** Requested displacement of this tick's rejected prediction, for backend-native route checks. */
    double failedTravel(UUID carriage) { return runtime.failedTravel.getOrDefault(carriage, 0.0); }

    /** Apply a backend's native velocity response to exactly one connected component. */
    void speedForConsist(UUID carriage, double speed) {
        VehicleRuntime.finite(speed, "consist speed");
        Group group = runtime.group(carriage);
        group.velocity(speed);
        for (Car member : group.cars()) for (BogeyDefinition bogey : member.definition.bogeys())
            runtime.bogeySpeeds.put(bogey.id(), speed);
    }

    /**
     * Stop one connected consist at a backend signal/station point. The fixed reference bogey
     * remains the reference: a bounded scalar correction finds its distance which places the
     * requested (possibly different) bogey at the point. Other consists and all controls survive.
     */
    void stopAtTrackPoint(UUID member, UUID bogeyId, double exactBogeyDistance) {
        VehicleRuntime.finite(exactBogeyDistance, "signal bogey distance");
        Group group = runtime.group(member);
        UUID bogeyOwner = runtime.bogeyOwners.get(bogeyId);
        if (bogeyOwner == null || !group.contains(bogeyOwner))
            throw new IllegalArgumentException("Signal bogey is not in the selected consist");
        runtime.path.sample(exactBogeyDistance);
        var definitions = group.cars().stream().map(car -> car.definition).toList();
        var links = runtime.connections.stream().filter(c -> group.contains(runtime.owner(c.first()))).toList();
        UUID reference = definitions.getFirst().referenceBogey().id();
        var current = new LinkedHashMap<UUID, Double>();
        for (var definition : definitions) for (var bogey : definition.bogeys())
            current.put(bogey.id(), runtime.placement.bogeyPositions().get(bogey.id()).distance());
        double initialError = current.get(bogeyId) - exactBogeyDistance;
        Solution solved = null;
        if (Math.abs(initialError) > 1e-8) {
            double previousDelta = 0, previousError = initialError;
            double delta = -initialError;
            // A signal correction belongs to the already travelled local interval, not a new
            // distant route branch. Refuse non-convergence without committing a partial pose.
            double maximumCorrection = Math.max(1, Math.abs(initialError) * 4);
            for (int iteration = 0; iteration < 16; iteration++) {
                if (!Double.isFinite(delta) || Math.abs(delta) > maximumCorrection)
                    throw new VehicleConstraintSolver.ConstraintFailure("Signal stop left its local motion interval");
                var proposed = new LinkedHashMap<UUID, Double>();
                double shift = delta;
                current.forEach((id, distance) -> proposed.put(id, distance + shift));
                solved = runtime.solver.solve(definitions, links, proposed, Map.of(reference, proposed.get(reference)), runtime.placement);
                double error = solved.bogeyPositions().get(bogeyId).distance() - exactBogeyDistance;
                if (Math.abs(error) <= 1e-7) break;
                double denominator = error - previousError;
                if (Math.abs(denominator) < 1e-12)
                    throw new VehicleConstraintSolver.ConstraintFailure("Signal stop distance did not converge");
                double next = delta - error * (delta - previousDelta) / denominator;
                previousDelta = delta;
                previousError = error;
                delta = next;
                solved = null;
            }
            if (solved == null) throw new VehicleConstraintSolver.ConstraintFailure("Signal stop distance did not converge");
        }
        if (solved != null) {
            var bodies = new LinkedHashMap<>(runtime.placement.bodyPoses()); bodies.putAll(solved.bodyPoses());
            var bogeys = new LinkedHashMap<>(runtime.placement.bogeyPositions()); bogeys.putAll(solved.bogeyPositions());
            var endpoints = new LinkedHashMap<>(runtime.placement.endpointPoses()); endpoints.putAll(solved.endpointPoses());
            var uncorrected = new LinkedHashMap<>(runtime.motionDistances);
            runtime.commit(new Solution(bodies, bogeys, endpoints, solved.brokenConnections()));
            for (Car car : runtime.cars.values()) if (!group.contains(car.definition.id()))
                car.definition.bogeys().forEach(b -> runtime.motionDistances.put(b.id(), uncorrected.get(b.id())));
        }
        // A native stop owns the integrated target as well as velocity, even when the last
        // visible pose was already at the requested point after a failed advance.
        for (Car car : group.cars()) for (BogeyDefinition bogey : car.definition.bogeys())
            runtime.motionDistances.put(bogey.id(), runtime.placement.bogeyPositions().get(bogey.id()).distance());
        group.velocity(0);
        for (var definition : definitions) for (var bogey : definition.bogeys()) runtime.bogeySpeeds.put(bogey.id(), 0.0);
    }

    /** Semi-implicit fixed substeps; swept contact/coupling events prevent high-speed tunnelling. */
    void tick(double seconds) {
        tick(seconds, Set.of());
    }

    /** Existing moving-block behaviors can temporarily hold their own connected component. */
    void tick(double seconds, Set<UUID> heldCarriages) {
        tick(seconds, heldCarriages, Set.of());
    }

    /** Paused components retain velocity and commands while their backend waits for geometry. */
    void tick(double seconds, Set<UUID> heldCarriages, Set<UUID> pausedCarriages) {
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 60)
            throw new IllegalArgumentException("Tick duration must be in (0, 60] seconds");
        Objects.requireNonNull(heldCarriages);
        Objects.requireNonNull(pausedCarriages);
        runtime.failedTravel.clear();
        var motion = new TickMotion(Set.copyOf(pausedCarriages));
        int steps = (int) Math.ceil(seconds / MAX_STEP);
        Map<UUID, Double> before = new LinkedHashMap<>(runtime.motionDistances);
        for (int i = 0; i < steps; i++) {
            step(seconds / steps, heldCarriages, motion);
        }
        runtime.motionDistances.forEach((id, distance) -> runtime.bogeySpeeds.put(id, (distance - before.get(id)) / seconds));
        if (!motion.failures.isEmpty()) runtime.constraintFailure = String.join("; ", new java.util.LinkedHashSet<>(motion.failures.values()));
    }

    private void step(double dt, Set<UUID> heldCarriages, TickMotion motion) {
        runtime.constraintFailure = null;
        for (Group group : runtime.groups()) {
            if (!heldCarriages.isEmpty() && group.cars().stream().anyMatch(car -> heldCarriages.contains(car.definition.id()))) {
                group.velocity(0);
                continue;
            }
            if (motion.paused(group)) continue;
            var forces = forces(group);
            double traction = forces.values().stream().mapToDouble(f -> f.traction()).sum();
            double opposition = forces.values().stream().mapToDouble(f -> Math.abs(f.brake()) + Math.abs(f.resistance())).sum();
            double net = forces.values().stream().mapToDouble(Force::net).sum();
            double previous = group.speed(), next = previous + net / group.mass() * dt;
            if (previous != 0 && Math.signum(previous) != Math.signum(next)) {
                double afterStop = Math.max(0, dt - Math.abs(previous * group.mass() / net));
                next = Math.abs(traction) > opposition
                        ? Math.signum(traction) * (Math.abs(traction) - opposition) / group.mass() * afterStop : 0;
            }
            group.velocity(next);
        }
        for (Approach approach : runtime.approaches) {
            Group source = runtime.group(runtime.owner(approach.first())), target = runtime.group(runtime.owner(approach.second()));
            if (!heldCarriages.isEmpty() && (source.cars().stream().anyMatch(car -> heldCarriages.contains(car.definition.id()))
                    || target.cars().stream().anyMatch(car -> heldCarriages.contains(car.definition.id())))) continue;
            if (motion.paused(source) || motion.paused(target)) continue;
            double direction = runtime.endpoint(approach.first()).facing() == CouplingBridge.CouplingEndpointFacing.NORTH ? 1 : -1;
            source.velocity(target.speed() + direction * approach.speedMps());
        }
        // Parked poses need no solve unless unavailable geometry left integrated travel
        // unapplied. That pending pose must recover even if the driver has since stopped.
        if (runtime.approaches.isEmpty() && runtime.cars.values().stream().allMatch(car -> car.speed == 0)
                && runtime.motionDistances.entrySet().stream().allMatch(entry ->
                        entry.getValue() == runtime.placement.bogeyPositions().get(entry.getKey()).distance())) return;
        double remaining = dt;
        int eventsLeft = Math.max(32, runtime.cars.size() * runtime.cars.size() * 4);
        while (remaining > VehicleRuntime.EPSILON) {
            List<Group> groups = runtime.groups();
            Event event;
            Prediction end = predicted(groups, remaining, motion);
            TickMotion events = motion.withFailures(end.failures());
            event = firstEvent(groups, end.placement(), remaining, events);
            double travelTime = event == null ? remaining : event.time();
            // Reuse the contact sweep's end pose: ordinary motion still solves only once.
            runtime.commit(travelTime == remaining ? end : predicted(groups, travelTime, motion), motion, groups, travelTime);
            remaining -= travelTime;
            if (event == null) break;
            if (--eventsLeft == 0) {
                failEvent(motion, event, "Contact solver did not converge");
                // A failed interaction cannot consume the rest of the dynamics step.
                runtime.commit(predicted(runtime.groups(), remaining, motion), motion, runtime.groups(), remaining);
                break;
            }
            if (event.coupling() != null) {
                if (!runtime.regionsMeet(runtime.placement, event.coupling().first(), event.coupling().second(), false)) {
                    failEvent(motion, event, "Coupling completion regions do not overlap");
                    continue;
                }
                try {
                    runtime.couple(event.coupling().first(), event.coupling().second());
                } catch (RuntimeException failure) {
                    failEvent(motion, event, failureMessage(failure));
                }
            }
            else resolveContact(event.first(), event.second(), motion);
        }
    }

    /** Simultaneous contact is solved as one impulse, not an endless sequence of pair impulses. */
    private void resolveContact(Group first, Group second, TickMotion motion) {
        var touching = new ArrayList<Group>();
        touching.add(first); touching.add(second);
        List<Group> all = runtime.groups();
        boolean added;
        do {
            added = false;
            for (Group candidate : all) {
                if (motion.failed(candidate)) continue;
                if (touching.stream().anyMatch(g -> g.contains(candidate.cars().getFirst().definition.id()))) continue;
                double mass = touching.stream().mapToDouble(Group::mass).sum();
                double velocity = touching.stream().mapToDouble(g -> g.mass() * g.speed()).sum() / mass;
                boolean compressed = false;
                for (Group contact : touching) {
                    if (runtime.manualPair(candidate, contact)) continue;
                    for (Car a : contact.cars()) for (Car b : candidate.cars()) {
                        if (a.distance <= b.distance) {
                            double gap = b.distance - b.definition.rearExtent() - a.distance - a.definition.frontExtent();
                            compressed |= gap <= VehicleRuntime.EPSILON && velocity >= b.speed;
                        } else {
                            double gap = a.distance - a.definition.rearExtent() - b.distance - b.definition.frontExtent();
                            compressed |= gap <= VehicleRuntime.EPSILON && b.speed >= velocity;
                        }
                    }
                }
                if (compressed) { touching.add(candidate); added = true; }
            }
        } while (added);
        double mass = touching.stream().mapToDouble(Group::mass).sum();
        double velocity = touching.stream().mapToDouble(g -> g.mass() * g.speed()).sum() / mass;
        touching.forEach(g -> g.velocity(velocity));
    }

    private Event firstEvent(List<Group> groups, Solution end, double duration, TickMotion motion) {
        Event event = null;
        for (Approach approach : runtime.approaches) {
            if (!runtime.couplingAvailable(runtime.endpoint(approach.first())) || !runtime.couplingAvailable(runtime.endpoint(approach.second()))) continue;
            if (motion.failed(runtime.group(runtime.owner(approach.first()))) || motion.failed(runtime.group(runtime.owner(approach.second())))) continue;
            var coupling = new Connection(approach.first(), approach.second());
            double time = couplingContactTime(coupling, groups, end, duration, false, motion);
            if (Double.isFinite(time) && time >= 0 && time <= duration && (event == null || time < event.time()))
                event = new Event(time, null, null, coupling);
        }
        // Hook contact is independent of body-impact handling (Create supplies the latter).
        // Only free, approaching completion points can connect; no attraction or speed override.
        var occupied = new HashSet<UUID>();
        for (Connection connection : runtime.connections) {
            occupied.add(connection.first()); occupied.add(connection.second());
        }
        var free = new ArrayList<List<CouplingEndpoint>>(groups.size());
        for (Group group : groups) {
            boolean manual = runtime.approaches.stream().anyMatch(a -> group.contains(runtime.owner(a.first())) || group.contains(runtime.owner(a.second())));
            free.add(manual || motion.failed(group) ? List.of() : group.cars().stream().flatMap(c -> c.definition.couplings().stream())
                    .filter(e -> e.kind() == CouplingBridge.CouplingEndpointKind.RIGID && !occupied.contains(e.id())
                            && runtime.couplingAvailable(e)).toList());
        }
        for (int i = 0; i < free.size(); i++) for (int j = i + 1; j < free.size(); j++) {
            for (CouplingEndpoint a : free.get(i)) for (CouplingEndpoint b : free.get(j)) {
                boolean opposing = a.facing() == CouplingBridge.CouplingEndpointFacing.NORTH && b.facing() == CouplingBridge.CouplingEndpointFacing.SOUTH
                        || a.facing() == CouplingBridge.CouplingEndpointFacing.SOUTH && b.facing() == CouplingBridge.CouplingEndpointFacing.NORTH;
                if (!opposing || !a.matches(b)) continue;
                var coupling = new Connection(a.id(), b.id());
                double time = couplingContactTime(coupling, groups, end, duration, true, motion);
                if (Double.isFinite(time) && time >= 0 && time <= duration && (event == null || time < event.time()))
                    event = new Event(time, null, null, coupling);
            }
        }
        if (!collisionEnabled) return event;
        // This remains a same-route longitudinal candidate/contact model. It is not a general
        // world-space collision implementation for bodies crossing on curves or different tracks.
        for (int i = 0; i < groups.size(); i++) for (int j = i + 1; j < groups.size(); j++) {
            Group a = groups.get(i), b = groups.get(j);
            if (runtime.manualPair(a, b) || motion.failed(a) || motion.failed(b)) continue;
            for (Car ca : a.cars()) for (Car cb : b.cars()) {
                boolean aBehind = ca.distance <= cb.distance;
                Car behind = aBehind ? ca : cb, ahead = aBehind ? cb : ca;
                double closing = (motion.paused(aBehind ? a : b) ? 0 : behind.speed)
                        - (motion.paused(aBehind ? b : a) ? 0 : ahead.speed);
                if (closing <= VehicleRuntime.EPSILON) continue;
                double gap = ahead.distance - ahead.definition.rearExtent()
                        - behind.distance - behind.definition.frontExtent();
                double time = Math.max(0, gap / closing);
                if (time <= duration && (event == null || time < event.time())) event = new Event(time, a, b, null);
            }
        }
        return event;
    }

    private double couplingContactTime(Connection coupling, List<Group> groups, Solution end, double duration,
                                       boolean natural, TickMotion motion) {
        boolean contact = runtime.regionsMeet(runtime.placement, coupling.first(), coupling.second(), false);
        if (!natural && contact) return 0;
        var direction = runtime.couplingDirection(runtime.placement, coupling.first());
        double before = VehicleRuntime.separation(runtime.placement, coupling, direction), after = VehicleRuntime.separation(end, coupling, direction);
        // A freshly uncoupled pair at rest, moving together or separating must stay released.
        if (natural && after >= before - VehicleRuntime.EPSILON) return Double.NaN;
        if (contact) return 0;
        if (before < -VehicleRuntime.PLACEMENT_TOLERANCE || after > 0) return Double.NaN;
        double low = 0, high = duration;
        Solution candidate = end;
        for (int i = 0; i < 40; i++) {
            double middle = (low + high) * .5;
            Prediction prediction = predicted(groups, middle, motion);
            if (prediction.failures().containsKey(runtime.owner(coupling.first()))
                    || prediction.failures().containsKey(runtime.owner(coupling.second()))) return Double.NaN;
            candidate = prediction.placement();
            if (VehicleRuntime.separation(candidate, coupling, direction) > 0) low = middle;
            else high = middle;
        }
        // The completion plane partitions a block-sized region. Transverse overlap, not
        // exact equality of the registered point coordinates, decides contact.
        if (!runtime.regionsMeet(candidate, coupling.first(), coupling.second(), false)) return Double.NaN;
        return (low + high) * .5;
    }

    private Prediction predicted(List<Group> groups, double seconds, TickMotion motion) {
        List<Group> active = groups.stream().filter(group -> !motion.paused(group)).toList();
        if (active.isEmpty()) return new Prediction(runtime.placement, new LinkedHashMap<>(runtime.motionDistances), Map.of());
        var proposed = new LinkedHashMap<>(runtime.motionDistances);
        for (Group group : active) for (Car car : group.cars()) for (BogeyDefinition bogey : car.definition.bogeys())
            proposed.compute(bogey.id(), (id, distance) -> distance + group.speed() * seconds);
        try {
            // Keep the ordinary single solve. Splitting is only necessary when a backend has
            // paused part of the domain or when that aggregate solve actually fails.
            Solution solved = active.size() == groups.size()
                    ? runtime.solver.solve(runtime.definitions(), runtime.connections, proposed, runtime.pins(proposed), runtime.placement)
                    : overlay(runtime.placement, solveGroups(active, proposed));
            return prediction(solved, proposed, Map.of());
        } catch (RuntimeException failure) {
            Solution result = runtime.placement;
            var failed = new LinkedHashMap<UUID, String>();
            if (active.size() == 1) {
                carriageIds(active).forEach(id -> failed.put(id, failureMessage(failure)));
                return prediction(result, proposed, failed);
            }
            // Isolate a missing pose without changing velocity, commands, topology or logical travel.
            for (Group group : active) {
                try {
                    result = overlay(result, solveGroups(List.of(group), proposed));
                } catch (RuntimeException componentFailure) {
                    carriageIds(List.of(group)).forEach(id -> failed.put(id, failureMessage(componentFailure)));
                }
            }
            return prediction(result, proposed, failed);
        }
    }

    private Prediction prediction(Solution solved, Map<UUID, Double> proposed, Map<UUID, String> failures) {
        // Successful follower placements determine their next starting cursors. Failed groups
        // keep the integrated cursors, not the last rendered/compatible entity's old position.
        solved.bogeyPositions().forEach((id, position) -> {
            if (position != runtime.placement.bogeyPositions().get(id) && !failures.containsKey(runtime.bogeyOwners.get(id)))
                proposed.put(id, position.distance());
        });
        return new Prediction(solved, proposed, failures);
    }

    void commit(Prediction prediction, TickMotion motion, List<Group> groups, double seconds) {
        runtime.commit(prediction.placement());
        runtime.motionDistances.putAll(prediction.distances());
        prediction.failures().forEach((id, reason) -> motion.failures.put(id, reason));
        for (Group group : groups) for (Car car : group.cars())
            if (prediction.failures().containsKey(car.definition.id()))
                runtime.failedTravel.merge(car.definition.id(), group.speed() * seconds, Double::sum);
    }

    Solution solveGroups(List<Group> selected, Map<UUID, Double> proposed) {
        Set<UUID> selectedIds = carriageIds(selected);
        List<CarriageDefinition> definitions = selected.stream().flatMap(group -> group.cars().stream())
                .map(car -> car.definition).toList();
        List<Connection> links = runtime.connections.stream().filter(link -> selectedIds.contains(runtime.owner(link.first()))).toList();
        var pins = new LinkedHashMap<UUID, Double>();
        for (Group group : selected) {
            UUID reference = group.cars().getFirst().definition.referenceBogey().id();
            pins.put(reference, proposed.get(reference));
        }
        return runtime.solver.solve(definitions, links, proposed, pins, runtime.placement);
    }

    static Solution overlay(Solution base, Solution update) {
        var bodies = new LinkedHashMap<>(base.bodyPoses()); bodies.putAll(update.bodyPoses());
        var bogeys = new LinkedHashMap<>(base.bogeyPositions()); bogeys.putAll(update.bogeyPositions());
        var endpoints = new LinkedHashMap<>(base.endpointPoses()); endpoints.putAll(update.endpointPoses());
        var broken = new ArrayList<>(base.brokenConnections()); broken.addAll(update.brokenConnections());
        return new Solution(bodies, bogeys, endpoints, broken);
    }

    private static Set<UUID> carriageIds(List<Group> groups) {
        var ids = new HashSet<UUID>();
        groups.forEach(group -> group.cars().forEach(car -> ids.add(car.definition.id())));
        return ids;
    }

    private void failEvent(TickMotion motion, Event event, String reason) {
        var affected = event.coupling() == null ? List.of(event.first(), event.second())
                : List.of(runtime.group(runtime.owner(event.coupling().first())), runtime.group(runtime.owner(event.coupling().second())));
        motion.fail(carriageIds(affected), reason);
    }

    private static String failureMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    Map<UUID, Force> forces(Group group) {
        double traction = 0, opposition = 0;
        boolean braking = false;
        for (Car car : group.cars()) for (BogeyDefinition bogey : car.definition.bogeys()) {
            TrainCommand control = car.controls.get(bogey.id());
            braking |= control.brake() > 0;
            traction += control.throttle() * bogey.availableTractionN(group.speed(),
                    wheelPowerMultiplier(car.definition.id()));
            opposition += control.brake() * bogey.parameters().brakeForceN() + bogey.parameters().runningResistanceN(group.speed());
        }
        double direction = Math.signum(group.speed() == 0 ? traction : group.speed());
        double scale = group.speed() == 0 && opposition > 0 ? Math.min(1, Math.abs(traction) / opposition) : 1;
        // Scale all forces together to preserve equilibrium speed and stopped force balance.
        double multiplier = braking ? brakingMultiplier : traction != 0 ? accelerationMultiplier : 1;
        var result = new HashMap<UUID, Force>();
        for (Car car : group.cars()) for (BogeyDefinition bogey : car.definition.bogeys()) {
            BogeyParameters parameters = bogey.parameters();
            TrainCommand control = car.controls.get(bogey.id());
            double drive = control.throttle() * bogey.availableTractionN(group.speed(),
                    wheelPowerMultiplier(car.definition.id()));
            double brake = -direction * control.brake() * parameters.brakeForceN() * scale;
            double resistance = -direction * parameters.runningResistanceN(group.speed()) * scale;
            VehicleBridge.VehicleRuntimeDriveMode mode = control.brake() > 0 ? VehicleBridge.VehicleRuntimeDriveMode.BRAKING
                    : drive != 0 ? VehicleBridge.VehicleRuntimeDriveMode.TRACTION : VehicleBridge.VehicleRuntimeDriveMode.COASTING;
            result.put(bogey.id(), new Force(drive * multiplier, brake * multiplier, resistance * multiplier,
                    mode == VehicleBridge.VehicleRuntimeDriveMode.BRAKING ? control.brake() : mode == VehicleBridge.VehicleRuntimeDriveMode.TRACTION ? Math.abs(control.throttle()) : 0, mode));
        }
        return result;
    }

    static double momentumVelocity(Group a, Group b) {
        return (a.mass() * a.speed() + b.mass() * b.speed()) / (a.mass() + b.mass());
    }

    record Force(double traction, double brake, double resistance, double load, VehicleBridge.VehicleRuntimeDriveMode mode) {
        double net() { return traction + brake + resistance; }
    }
    record Event(double time, Group first, Group second, Connection coupling) { }
    record Prediction(Solution placement, Map<UUID, Double> distances, Map<UUID, String> failures) { }
    static final class TickMotion {
        final Set<UUID> paused;
        final Map<UUID, String> failures = new LinkedHashMap<>();
        TickMotion(Set<UUID> paused) { this.paused = paused; }
        boolean paused(Group group) {
            return group.cars().stream().anyMatch(car -> paused.contains(car.definition.id()));
        }
        boolean failed(Group group) {
            return group.cars().stream().anyMatch(car -> failures.containsKey(car.definition.id()));
        }
        void fail(Set<UUID> carriages, String reason) { carriages.forEach(id -> failures.putIfAbsent(id, reason)); }
        TickMotion withFailures(Map<UUID, String> additional) {
            var result = new TickMotion(paused);
            result.failures.putAll(failures);
            result.failures.putAll(additional);
            return result;
        }
    }
}
