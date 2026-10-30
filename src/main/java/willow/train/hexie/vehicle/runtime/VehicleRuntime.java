package willow.train.hexie.vehicle.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.Solution;
import willow.train.hexie.vehicle.track.TrackGeometry;
import willow.train.hexie.vehicle.track.TrackPath;

/**
 * A server-owned route domain. Only put carriages on the same comparable route
 * in one runtime. Independently solved bogey positions and rigid body poses are authoritative. No world
 * blocks, Create control, rendering or external physics are owned here. One block equals one metre.
 * Each bogey contributes its registered supported mass and forces. Linked cars share momentum;
 * touching unlinked cars exchange a non-rebounding contact impulse without becoming linked.
 */
public final class VehicleRuntime {
    private static final double EPSILON = 1e-8;
    private static final double PLACEMENT_TOLERANCE = 1e-5;
    private static final double MAX_STEP = 0.05;
    private final Map<UUID, Car> cars = new LinkedHashMap<>();
    private final Map<UUID, UUID> bogeyOwners = new HashMap<>();
    private final Map<UUID, UUID> endpointOwners = new HashMap<>();
    private final List<Connection> connections = new ArrayList<>();
    private final List<Approach> approaches = new ArrayList<>();
    private final Map<UUID, Double> bogeySpeeds = new LinkedHashMap<>();
    private final Map<UUID, Double> failedTravel = new HashMap<>();
    private final TrackPath path;
    private final VehicleConstraintSolver solver;
    private Solution placement = new Solution(Map.of(), Map.of(), Map.of());
    private String constraintFailure;
    private boolean collisionEnabled = true;
    private ConstraintFailurePolicy constraintFailurePolicy = ConstraintFailurePolicy.STOP_ALL;
    private long consistRevision;

    public VehicleRuntime() { this(TrackPath.straight()); }
    public VehicleRuntime(TrackPath path) {
        this.path = Objects.requireNonNull(path);
        solver = new VehicleConstraintSolver(path);
    }
    public Solution placement() { return placement; }
    public CarriageDefinition definition(UUID id) { return car(id).definition; }
    public double bogeySpeed(UUID id) { return Objects.requireNonNull(bogeySpeeds.get(id), "Unknown bogey"); }
    public Optional<String> constraintFailure() { return Optional.ofNullable(constraintFailure); }
    public boolean collisionEnabled() { return collisionEnabled; }
    /** Backend policy: Create owns body impacts; manual and contact coupling remain active. */
    public void collisionEnabled(boolean enabled) { collisionEnabled = enabled; }
    public ConstraintFailurePolicy constraintFailurePolicy() { return constraintFailurePolicy; }
    /** Backend-owned motion can retry a failed consist without erasing its velocity or commands. */
    public void constraintFailurePolicy(ConstraintFailurePolicy policy) {
        constraintFailurePolicy = Objects.requireNonNull(policy);
    }
    /** Changes only when membership/connections change, never for motion or controls. */
    public long consistRevision() { return consistRevision; }

    public static final class RouteMismatchException extends IllegalArgumentException {
        private RouteMismatchException(RuntimeException cause) { super("Saved track geometry no longer matches its route", cause); }
    }

    public enum DriveMode { TRACTION, BRAKING, COASTING }
    public enum ConstraintFailurePolicy { STOP_ALL, RETRY_CONSIST }

    public record Control(double throttle, double brake) {
        public static final Control NEUTRAL = new Control(0, 0);
        public Control {
            if (!Double.isFinite(throttle) || Math.abs(throttle) > 1
                    || !Double.isFinite(brake) || brake < 0 || brake > 1)
                throw new IllegalArgumentException("Control must be throttle [-1,1], brake [0,1]");
        }
    }

    /** Signed wheel RPM, absolute drive RPM and [0,1] commanded drive/braking load for sound. */
    public record BogeyState(UUID id, String modelId, double distance, double supportedMassKg,
                             double tractionForceN, double brakeForceN, double resistanceForceN,
                             double wheelRpm, double driveRpm, double load, DriveMode mode) {
        public double netForceN() { return tractionForceN + brakeForceN + resistanceForceN; }
    }

    public record CarriageState(UUID id, double distance, double speed, double massKg,
                                List<BogeyState> bogeys, List<UUID> consist) {
        public CarriageState { bogeys = List.copyOf(bogeys); consist = List.copyOf(consist); }
    }

    public record Connection(UUID first, UUID second) {
        public Connection { Objects.requireNonNull(first); Objects.requireNonNull(second); }
    }

    public record Approach(UUID first, UUID second, double speedMps) {
        public Approach {
            Objects.requireNonNull(first); Objects.requireNonNull(second);
            if (!Double.isFinite(speedMps) || speedMps <= 0) throw new IllegalArgumentException("Approach speed");
        }
    }

    public record CouplingState(UUID first, UUID second, double progress, boolean connected,
                                CouplingEndpoint.Kind kind) { }

    public record SavedCarriage(CarriageDefinition definition, double distance, double speed,
                                Map<UUID, Control> controls) {
        public SavedCarriage { Objects.requireNonNull(definition); controls = Map.copyOf(controls); }
    }

    public record Checkpoint(List<SavedCarriage> carriages, List<Connection> connections,
                             List<Approach> approaches, Solution placement, Map<UUID, Double> bogeySpeeds) {
        public Checkpoint {
            carriages = List.copyOf(carriages);
            connections = List.copyOf(connections);
            approaches = List.copyOf(approaches);
            Objects.requireNonNull(placement);
            bogeySpeeds = Map.copyOf(bogeySpeeds);
        }
    }

    public void addCarriage(CarriageDefinition definition, double distance, double speed) {
        installCarriage(definition, distance, speed);
        var proposed = distances(placement);
        for (BogeyDefinition bogey : definition.bogeys())
            proposed.put(bogey.id(), distance + bogey.offset() + bogey.mount().trackPivot().z());
        try {
            commit(solver.solve(definitions(), connections, proposed, pins(proposed), placement));
            definition.bogeys().forEach(b -> bogeySpeeds.put(b.id(), speed));
            consistRevision++;
        } catch (RuntimeException failure) {
            cars.remove(definition.id());
            definition.bogeys().forEach(b -> bogeyOwners.remove(b.id()));
            definition.couplings().forEach(e -> endpointOwners.remove(e.id()));
            throw failure;
        }
    }

    private void installCarriage(CarriageDefinition definition, double distance, double speed) {
        Objects.requireNonNull(definition);
        finite(distance, "distance"); finite(speed, "speed");
        if (cars.containsKey(definition.id())) throw new IllegalArgumentException("Duplicate carriage UUID");
        for (BogeyDefinition bogey : definition.bogeys())
            if (bogeyOwners.containsKey(bogey.id())) throw new IllegalArgumentException("Duplicate bogey UUID");
        for (CouplingEndpoint endpoint : definition.couplings())
            if (endpointOwners.containsKey(endpoint.id())) throw new IllegalArgumentException("Duplicate endpoint UUID");
        var car = new Car(definition, distance, speed);
        cars.put(definition.id(), car);
        definition.bogeys().forEach(b -> bogeyOwners.put(b.id(), definition.id()));
        definition.couplings().forEach(e -> endpointOwners.put(e.id(), definition.id()));
    }

    public void removeCarriage(UUID id) {
        Car car = car(id);
        if (group(id).cars.size() != 1 || approaches.stream().anyMatch(a -> involved(a, id)))
            throw new IllegalStateException("Uncouple and cancel manual coupling before removing a carriage");
        cars.remove(id);
        car.definition.bogeys().forEach(b -> bogeyOwners.remove(b.id()));
        car.definition.couplings().forEach(e -> endpointOwners.remove(e.id()));
        var bodies = new LinkedHashMap<>(placement.bodies());
        var bogeys = new LinkedHashMap<>(placement.bogeys());
        var endpoints = new LinkedHashMap<>(placement.endpoints());
        bodies.remove(id);
        car.definition.bogeys().forEach(b -> { bogeys.remove(b.id()); bogeySpeeds.remove(b.id()); });
        car.definition.couplings().forEach(e -> endpoints.remove(e.id()));
        placement = new Solution(bodies, bogeys, endpoints);
        consistRevision++;
    }

    public List<CarriageDefinition> definitions() { return cars.values().stream().map(c -> c.definition).toList(); }

    /** Entity/control adapters need velocity without rebuilding diagnostic force/consist reports. */
    public double speed(UUID carriage) { return car(carriage).speed; }
    /** Requested displacement of this tick's rejected prediction, for backend-native route checks. */
    public double failedTravel(UUID carriage) { return failedTravel.getOrDefault(carriage, 0.0); }

    /** Apply a backend's native velocity response to exactly one connected component. */
    public void speedForConsist(UUID carriage, double speed) {
        finite(speed, "consist speed");
        Group group = group(carriage);
        group.velocity(speed);
        for (Car member : group.cars) for (BogeyDefinition bogey : member.definition.bogeys())
            bogeySpeeds.put(bogey.id(), speed);
    }

    /** Direction requested by the connected bogeys before a stopped train has advanced. */
    public double tractionDirection(UUID carriage) {
        double traction = 0;
        for (Car member : group(carriage).cars) for (BogeyDefinition bogey : member.definition.bogeys()) {
            Control control = member.controls.get(bogey.id());
            if (control.brake == 0) traction += control.throttle * bogey.parameters().tractionForceN();
        }
        return Math.signum(traction);
    }

    public void controlsForConsist(UUID carriage, Control control) {
        Objects.requireNonNull(control);
        for (Car member : group(carriage).cars) member.controls.replaceAll((id, previous) -> control);
    }

    public boolean connected(UUID first, UUID second) { return group(first).contains(second); }

    /** Backend disappearance is a stopped runtime, never permission to use stale track samples. */
    public void halt(String reason) {
        stopForConstraint(Objects.requireNonNull(reason));
        cars.values().forEach(car -> car.controls.replaceAll((id, previous) -> Control.NEUTRAL));
        bogeySpeeds.replaceAll((id, previous) -> 0.0);
    }

    public void controls(UUID carriage, Control control) {
        Objects.requireNonNull(control);
        car(carriage).controls.replaceAll((id, previous) -> control);
    }

    public void controlBogey(UUID bogey, Control control) {
        Objects.requireNonNull(control);
        UUID owner = bogeyOwners.get(bogey);
        if (owner == null) throw new IllegalArgumentException("Unknown bogey " + bogey);
        car(owner).controls.put(bogey, control);
    }

    public CarriageState state(UUID id) {
        Car car = car(id);
        Group group = group(id);
        var forces = forces(group);
        var bogeys = new ArrayList<BogeyState>();
        for (BogeyDefinition bogey : car.definition.bogeys()) {
            Force force = forces.get(bogey.id());
            double rpm = bogeySpeeds.get(bogey.id()) * 60 / (2 * Math.PI * bogey.parameters().wheelRadiusM());
            bogeys.add(new BogeyState(bogey.id(), bogey.modelId(), placement.bogeys().get(bogey.id()).distance(),
                    bogey.parameters().loadKg(), force.traction, force.brake, force.resistance,
                    rpm, Math.abs(rpm) * bogey.parameters().driveRatio(), force.load, force.mode));
        }
        return new CarriageState(id, car.distance, car.speed, car.definition.massKg(), bogeys,
                group.cars.stream().map(c -> c.definition.id()).toList());
    }

    public List<CarriageState> states() { return cars.keySet().stream().map(this::state).toList(); }

    /** Only connects when the completion points actually coincide. It never snaps or attracts. */
    public void couple(UUID first, UUID second) {
        validatePair(first, second);
        if (endpointGap(placement, first, second) > PLACEMENT_TOLERANCE)
            throw new IllegalStateException("Coupling completion points do not coincide");
        Group a = group(owner(first)), b = group(owner(second));
        double velocity = momentumVelocity(a, b);
        connections.add(new Connection(first, second));
        Solution connected;
        try {
            var current = distances(placement);
            connected = constraintFailurePolicy == ConstraintFailurePolicy.RETRY_CONSIST
                    ? overlay(placement, solveGroups(List.of(group(owner(first))), current))
                    : solver.solve(definitions(), connections, current, pins(current), placement);
            requireSamePlacement(placement, connected);
        } catch (RuntimeException failure) {
            connections.removeLast();
            throw failure;
        }
        commit(connected);
        // A direct connection may use a different free endpoint of a manually moving consist.
        // Cancel those requests too, or a newly merged consist can acquire conflicting shunting controls.
        approaches.removeIf(p -> a.contains(owner(p.first)) || a.contains(owner(p.second))
                || b.contains(owner(p.first)) || b.contains(owner(p.second)));
        a.velocity(velocity); b.velocity(velocity);
        consistRevision++;
    }

    public void uncouple(UUID endpoint) {
        endpoint(endpoint);
        List<Connection> previous = List.copyOf(connections);
        connections.removeIf(c -> c.first.equals(endpoint) || c.second.equals(endpoint));
        if (connections.size() == previous.size()) return;
        try {
            var current = distances(placement);
            Solution unlinked = solver.solve(definitions(), connections, current, pins(current), placement);
            requireSamePlacement(placement, unlinked, false);
            commit(unlinked);
            consistRevision++;
        } catch (RuntimeException failure) {
            connections.clear(); connections.addAll(previous);
            throw failure;
        }
    }

    /** Explicit shunting request: the first car and its consist approach the second endpoint. */
    public void beginCoupling(UUID first, UUID second, double approachSpeedMps) {
        validatePair(first, second);
        Approach approach = new Approach(first, second, approachSpeedMps);
        validateApproachParticipants(first, second);
        if (endpointGap(placement, first, second) <= PLACEMENT_TOLERANCE) couple(first, second);
        else approaches.add(approach);
    }

    private void validateApproachParticipants(UUID first, UUID second) {
        Set<UUID> affected = new HashSet<>();
        group(owner(first)).cars.forEach(c -> affected.add(c.definition.id()));
        group(owner(second)).cars.forEach(c -> affected.add(c.definition.id()));
        if (approaches.stream().anyMatch(a -> affected.contains(owner(a.first)) || affected.contains(owner(a.second))))
            throw new IllegalStateException("A consist already participates in manual coupling");
    }

    public void cancelCoupling(UUID endpoint) {
        endpoint(endpoint);
        approaches.removeIf(a -> a.first.equals(endpoint) || a.second.equals(endpoint));
    }

    public List<CouplingState> couplingStates() {
        var result = new ArrayList<CouplingState>();
        for (Connection connection : connections)
            result.add(new CouplingState(connection.first, connection.second, 1, true, endpoint(connection.first).kind()));
        for (Approach approach : approaches) {
            double distance = endpointGap(placement, approach.first, approach.second);
            double stroke = endpoint(approach.first).engagementLength() + endpoint(approach.second).engagementLength();
            double progress = stroke > EPSILON ? Math.clamp(1 - distance / stroke, 0, 1) : distance <= EPSILON ? 1 : 0;
            result.add(new CouplingState(approach.first, approach.second, progress, false, endpoint(approach.first).kind()));
        }
        return List.copyOf(result);
    }

    public Checkpoint checkpoint() {
        return new Checkpoint(cars.values().stream().map(c -> new SavedCarriage(c.definition,
                c.distance, c.speed, c.controls)).toList(), connections, approaches, placement, bogeySpeeds);
    }

    /**
     * Replace an extended route whose existing geometry is unchanged. Only the route coordinate
     * origin moves: preserve the exact solved body/bogey/endpoint poses and ongoing controls.
     * Restore validates the new path at every support before this replacement can be installed.
     */
    public VehicleRuntime rebind(TrackPath target, double distanceOffset) {
        finite(distanceOffset, "route coordinate offset");
        var shiftedCars = cars.values().stream().map(car -> new SavedCarriage(car.definition,
                car.distance + distanceOffset, car.speed, car.controls)).toList();
        var shiftedBogeys = new LinkedHashMap<UUID, VehicleConstraintSolver.BogeyPosition>();
        placement.bogeys().forEach((id, p) -> shiftedBogeys.put(id, new VehicleConstraintSolver.BogeyPosition(
                p.distance() + distanceOffset, p.lateral(), p.vertical(), p.track(), p.pivot(), p.attachment(),
                p.position(), p.suspensionLateral(), p.offRailOffset())));
        var rebound = restore(new Checkpoint(shiftedCars, connections, approaches,
                new Solution(placement.bodies(), shiftedBogeys, placement.endpoints()), bogeySpeeds), target);
        rebound.collisionEnabled = collisionEnabled;
        rebound.constraintFailurePolicy = constraintFailurePolicy;
        rebound.consistRevision = consistRevision;
        return rebound;
    }

    /** One-time backend geometry upgrade, mapping each saved support by its original edge parameter. */
    public VehicleRuntime remapRoute(TrackPath target, java.util.function.DoubleUnaryOperator mapDistance) {
        Objects.requireNonNull(mapDistance);
        var updated = restoreSavedState(checkpoint(), target);
        var proposed = new LinkedHashMap<UUID, Double>();
        placement.bogeys().forEach((id, support) -> {
            double mapped = mapDistance.applyAsDouble(support.distance());
            target.sample(mapped);
            proposed.put(id, mapped);
        });
        // A previous backend may have sampled a secondary curve that differs from the visible
        // primary. Re-solve the same links on the corrected geometry, retaining speeds/controls.
        updated.commit(updated.solver.solve(updated.definitions(), updated.connections,
                proposed, updated.pins(proposed), null));
        updated.collisionEnabled = collisionEnabled;
        updated.constraintFailurePolicy = constraintFailurePolicy;
        updated.consistRevision = consistRevision;
        return updated;
    }

    /**
     * Combine two already aligned route domains without moving their world geometry or connecting
     * them automatically. The target keeps the first domain's direction. A reversed second domain
     * changes its normalized local frame by a half turn; adapters must likewise reverse each
     * affected carriage's captured-model basis. Source checkpoints remain untouched.
     */
    public static VehicleRuntime merge(TrackPath target, Checkpoint first, double firstOffset,
                                        Checkpoint second, double secondOffset, boolean reverseSecond) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        finite(firstOffset, "first route offset");
        finite(secondOffset, "second route offset");
        // Validate ownership, controls, links and diagnostic coordinates before transforming them.
        // This stage deliberately does not query either old route, which may be a shorter binding.
        restoreSavedState(first, target);
        restoreSavedState(second, target);
        Checkpoint a = remapCheckpoint(first, firstOffset, false);
        Checkpoint b = remapCheckpoint(second, secondOffset, reverseSecond);
        var carriages = new ArrayList<>(a.carriages);
        carriages.addAll(b.carriages);
        var links = new ArrayList<>(a.connections);
        links.addAll(b.connections);
        var pending = new ArrayList<>(a.approaches);
        pending.addAll(b.approaches);
        var bodies = new LinkedHashMap<>(a.placement.bodies()); bodies.putAll(b.placement.bodies());
        var bogeys = new LinkedHashMap<>(a.placement.bogeys()); bogeys.putAll(b.placement.bogeys());
        var endpoints = new LinkedHashMap<>(a.placement.endpoints()); endpoints.putAll(b.placement.endpoints());
        var speeds = new LinkedHashMap<>(a.bogeySpeeds); speeds.putAll(b.bogeySpeeds);
        // Strict restore checks every remapped rail frame and re-solves the existing constraints.
        // An incorrect offset/direction or duplicate identity fails before either source is changed.
        return restore(new Checkpoint(carriages, links, pending, new Solution(bodies, bogeys, endpoints), speeds), target);
    }

    private static Checkpoint remapCheckpoint(Checkpoint source, double offset, boolean reverse) {
        double sign = reverse ? -1 : 1;
        var carriages = new ArrayList<SavedCarriage>();
        for (SavedCarriage saved : source.carriages) {
            CarriageDefinition definition = saved.definition;
            if (reverse) {
                var bogeys = definition.bogeys().stream().map(b -> new BogeyDefinition(b.id(), b.modelId(), -b.offset(),
                        b.geometry(), b.parameters(), new BogeyMount(halfTurn(b.mount().bodyPoint()),
                                halfTurn(b.mount().trackPivot()), b.mount().type(), b.mount().lateralLimit()))).toList();
                var endpoints = definition.couplings().stream().map(e -> new CouplingEndpoint(e.id(),
                        halfTurn(e.blockCenter()), switch (e.facing()) {
                            case NORTH -> CouplingEndpoint.Facing.SOUTH;
                            case EAST -> CouplingEndpoint.Facing.WEST;
                            case SOUTH -> CouplingEndpoint.Facing.NORTH;
                            case WEST -> CouplingEndpoint.Facing.EAST;
                        }, e.pivot(), e.start(), e.complete(), e.kind())).toList();
                definition = new CarriageDefinition(definition.id(), bogeys, endpoints,
                        definition.frontExtent(), definition.rearExtent());
            }
            var controls = new LinkedHashMap<UUID, Control>();
            saved.controls.forEach((id, control) -> controls.put(id, reverse
                    ? new Control(-control.throttle, control.brake) : control));
            carriages.add(new SavedCarriage(definition, sign * saved.distance + offset, sign * saved.speed, controls));
        }
        var bodies = new LinkedHashMap<UUID, VehicleConstraintSolver.BodyPose>();
        source.placement.bodies().forEach((id, pose) -> bodies.put(id, reverse
                ? new VehicleConstraintSolver.BodyPose(pose.position(), pose.yaw() + Math.PI, -pose.pitch(), -pose.roll())
                : pose));
        var bogeys = new LinkedHashMap<UUID, VehicleConstraintSolver.BogeyPosition>();
        source.placement.bogeys().forEach((id, support) -> {
            var sample = support.track();
            if (reverse) sample = TrackGeometry.Sample.restoreNormalized(sample.position(), sample.tangent().scale(-1),
                    sample.normal(), -sample.cantRadians());
            bogeys.put(id, new VehicleConstraintSolver.BogeyPosition(sign * support.distance() + offset,
                    sign * support.lateral(), support.vertical(), sample, support.pivot(), support.attachment(),
                    support.position(), sign * support.suspensionLateral(), support.offRailOffset()));
        });
        var speeds = new LinkedHashMap<UUID, Double>();
        source.bogeySpeeds.forEach((id, speed) -> speeds.put(id, sign * speed));
        return new Checkpoint(carriages, source.connections, source.approaches,
                new Solution(bodies, bogeys, source.placement.endpoints()), speeds);
    }

    private static CouplingEndpoint.Point halfTurn(CouplingEndpoint.Point point) {
        return new CouplingEndpoint.Point(-point.x(), point.y(), -point.z());
    }

    /**
     * Stop one connected consist at a backend signal/station point. The fixed reference bogey
     * remains the reference: a bounded scalar correction finds its distance which places the
     * requested (possibly different) bogey at the point. Other consists and all controls survive.
     */
    public void stopAtTrackPoint(UUID member, UUID bogeyId, double exactBogeyDistance) {
        finite(exactBogeyDistance, "signal bogey distance");
        Group group = group(member);
        UUID bogeyOwner = bogeyOwners.get(bogeyId);
        if (bogeyOwner == null || !group.contains(bogeyOwner))
            throw new IllegalArgumentException("Signal bogey is not in the selected consist");
        path.sample(exactBogeyDistance);
        var definitions = group.cars.stream().map(car -> car.definition).toList();
        var links = connections.stream().filter(c -> group.contains(owner(c.first))).toList();
        UUID reference = definitions.getFirst().referenceBogey().id();
        var current = new LinkedHashMap<UUID, Double>();
        for (var definition : definitions) for (var bogey : definition.bogeys())
            current.put(bogey.id(), placement.bogeys().get(bogey.id()).distance());
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
                solved = solver.solve(definitions, links, proposed, Map.of(reference, proposed.get(reference)), placement);
                double error = solved.bogeys().get(bogeyId).distance() - exactBogeyDistance;
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
            var bodies = new LinkedHashMap<>(placement.bodies()); bodies.putAll(solved.bodies());
            var bogeys = new LinkedHashMap<>(placement.bogeys()); bogeys.putAll(solved.bogeys());
            var endpoints = new LinkedHashMap<>(placement.endpoints()); endpoints.putAll(solved.endpoints());
            placement = new Solution(bodies, bogeys, endpoints);
            for (Car car : group.cars) {
                var first = car.definition.bogeys().getFirst();
                car.distance = placement.bogeys().get(first.id()).distance() - first.offset() - first.mount().trackPivot().z();
            }
        }
        group.velocity(0);
        for (var definition : definitions) for (var bogey : definition.bogeys()) bogeySpeeds.put(bogey.id(), 0.0);
    }

    /**
     * Rebuild this whole route domain against explicitly mapped carriage reference distances.
     * No state is changed on this runtime if validation/placement fails. Definitions, hard links
     * and pending approach requests survive; approach progress follows the new endpoint geometry.
     */
    public VehicleRuntime rebase(TrackPath target, Map<UUID, Double> carriageDistances, boolean stop) {
        return rebase(target, carriageDistances, stop ? 0.0 : null, stop);
    }

    /** Reposition with one explicit velocity for the whole domain; clear prior driver commands. */
    public VehicleRuntime rebase(TrackPath target, Map<UUID, Double> carriageDistances, double speed) {
        finite(speed, "relocation speed");
        return rebase(target, carriageDistances, speed, true);
    }

    /** Recover complete consists on a new route even if the saved route is unavailable. */
    public static VehicleRuntime relocateCheckpoint(Checkpoint source, TrackPath target,
                                                     Map<UUID, Double> carriageDistances) {
        Objects.requireNonNull(carriageDistances);
        // Validate the complete saved domain before selecting it, without querying its old track.
        restoreSavedState(source, target);
        var selected = subsetCheckpoint(source, carriageDistances.keySet());
        return restoreSavedState(selected, target).rebase(target, carriageDistances, true);
    }

    private VehicleRuntime rebase(TrackPath target, Map<UUID, Double> carriageDistances,
                                  Double speedOverride, boolean clearControls) {
        Objects.requireNonNull(carriageDistances);
        if (!carriageDistances.keySet().equals(cars.keySet()))
            throw new IllegalArgumentException("Rebase requires a distance for every carriage in the route domain");
        var moved = new VehicleRuntime(target);
        var proposed = new LinkedHashMap<UUID, Double>();
        for (Car car : cars.values()) {
            double distance = Objects.requireNonNull(carriageDistances.get(car.definition.id()));
            double speed = speedOverride == null ? car.speed : speedOverride;
            moved.installCarriage(car.definition, distance, speed);
            if (!clearControls) moved.car(car.definition.id()).controls.putAll(car.controls);
            for (BogeyDefinition bogey : car.definition.bogeys()) {
                double cursor = distance + bogey.offset() + bogey.mount().trackPivot().z();
                // Validate every registered support before solving; endpoints are not the whole body.
                target.sample(cursor);
                proposed.put(bogey.id(), cursor);
                moved.bogeySpeeds.put(bogey.id(), speedOverride == null ? bogeySpeeds.get(bogey.id()) : speed);
            }
        }
        for (Connection connection : connections) {
            moved.validatePair(connection.first, connection.second);
            moved.connections.add(connection);
        }
        // A cold solve avoids carrying a distant old-world pose into the new track's local branch.
        moved.commit(moved.solver.solve(moved.definitions(), moved.connections, proposed, moved.pins(proposed), null));
        for (Approach approach : approaches) {
            moved.validatePair(approach.first, approach.second);
            moved.validateApproachParticipants(approach.first, approach.second);
            moved.approaches.add(approach);
        }
        moved.collisionEnabled = collisionEnabled;
        moved.constraintFailurePolicy = constraintFailurePolicy;
        moved.consistRevision = consistRevision;
        return moved;
    }

    /**
     * Copy whole connected components for a backend/domain split. A hard link may not be cut.
     * Manual approaches crossing this boundary are cancelled in the copy, never converted to links.
     * The caller retains/replaces the complementary domain explicitly; this method removes no cars.
     */
    public VehicleRuntime subset(Set<UUID> carriageIds) {
        var copied = restore(subsetCheckpoint(checkpoint(), carriageIds), path);
        copied.collisionEnabled = collisionEnabled;
        copied.constraintFailurePolicy = constraintFailurePolicy;
        copied.consistRevision = consistRevision;
        return copied;
    }

    /** Select saved complete consists without resolving or resampling their previous route. */
    public static Checkpoint subsetCheckpoint(Checkpoint source, Set<UUID> carriageIds) {
        Set<UUID> selected = Set.copyOf(carriageIds);
        var carIds = new HashSet<UUID>();
        var endpointOwners = new HashMap<UUID, UUID>();
        for (SavedCarriage saved : source.carriages) {
            if (!carIds.add(saved.definition.id()))
                throw new IllegalArgumentException("Duplicate checkpoint carriage");
            for (CouplingEndpoint endpoint : saved.definition.couplings())
                if (endpointOwners.putIfAbsent(endpoint.id(), saved.definition.id()) != null)
                    throw new IllegalArgumentException("Duplicate checkpoint endpoint");
        }
        if (!carIds.containsAll(selected)) throw new IllegalArgumentException("Unknown subset carriage");
        for (Connection connection : source.connections) {
            UUID first = Objects.requireNonNull(endpointOwners.get(connection.first), "Unknown connected endpoint");
            UUID second = Objects.requireNonNull(endpointOwners.get(connection.second), "Unknown connected endpoint");
            if (selected.contains(first) != selected.contains(second))
                throw new IllegalArgumentException("Subset cannot cut a connected consist");
        }
        for (Approach approach : source.approaches) {
            Objects.requireNonNull(endpointOwners.get(approach.first), "Unknown approach endpoint");
            Objects.requireNonNull(endpointOwners.get(approach.second), "Unknown approach endpoint");
        }
        var selectedCars = source.carriages.stream().filter(car -> selected.contains(car.definition.id())).toList();
        var selectedConnections = source.connections.stream().filter(c -> selected.contains(endpointOwners.get(c.first))).toList();
        var selectedApproaches = source.approaches.stream().filter(a -> selected.contains(endpointOwners.get(a.first))
                && selected.contains(endpointOwners.get(a.second))).toList();
        var bodies = new LinkedHashMap<UUID, VehicleConstraintSolver.BodyPose>();
        var bogeys = new LinkedHashMap<UUID, VehicleConstraintSolver.BogeyPosition>();
        var endpoints = new LinkedHashMap<UUID, VehicleConstraintSolver.EndpointPose>();
        var speeds = new LinkedHashMap<UUID, Double>();
        for (SavedCarriage car : selectedCars) {
            bodies.put(car.definition.id(), source.placement.bodies().get(car.definition.id()));
            for (BogeyDefinition bogey : car.definition.bogeys()) {
                bogeys.put(bogey.id(), source.placement.bogeys().get(bogey.id()));
                speeds.put(bogey.id(), source.bogeySpeeds.get(bogey.id()));
            }
            for (CouplingEndpoint endpoint : car.definition.couplings())
                endpoints.put(endpoint.id(), source.placement.endpoints().get(endpoint.id()));
        }
        return new Checkpoint(selectedCars, selectedConnections, selectedApproaches,
                new Solution(bodies, bogeys, endpoints), speeds);
    }

    public static VehicleRuntime restore(Checkpoint checkpoint) {
        return restore(checkpoint, TrackPath.straight());
    }

    public static VehicleRuntime restore(Checkpoint checkpoint, TrackPath path) {
        return restore(checkpoint, path, false);
    }

    /** One-time upgrade of snapshots written before rigid end-support constraints were enforced. */
    public static VehicleRuntime restoreLegacyPlacement(Checkpoint checkpoint, TrackPath path) {
        return restore(checkpoint, path, true);
    }

    private static VehicleRuntime restore(Checkpoint checkpoint, TrackPath path, boolean upgradePlacement) {
        var runtime = restoreSavedState(checkpoint, path);
        var savedDistances = distances(runtime.placement);
        // Distinguish an edited/unavailable external route from malformed carriage/control NBT.
        // The adapter can retain the saved vehicle while waiting for its track backend.
        try {
            for (var saved : runtime.placement.bogeys().values()) {
                var current = path.sample(saved.distance());
                close(saved.track().position(), current.position());
                close(saved.track().tangent(), current.tangent());
                close(saved.track().normal(), current.normal());
                close(saved.track().cantRadians(), current.cantRadians());
            }
        } catch (RuntimeException failure) { throw new RouteMismatchException(failure); }
        var validated = runtime.solver.solve(runtime.definitions(), runtime.connections, savedDistances,
                runtime.pins(savedDistances), runtime.placement);
        if (upgradePlacement) runtime.commit(validated);
        else requireSamePlacement(runtime.placement, validated);
        return runtime;
    }

    /** Restore and validate saved data independently from availability of the old track geometry. */
    private static VehicleRuntime restoreSavedState(Checkpoint checkpoint, TrackPath path) {
        var runtime = new VehicleRuntime(path);
        for (SavedCarriage saved : checkpoint.carriages) {
            runtime.installCarriage(saved.definition, saved.distance, saved.speed);
            Car car = runtime.car(saved.definition.id());
            if (!saved.controls.keySet().equals(car.controls.keySet()))
                throw new IllegalArgumentException("Checkpoint bogey controls do not match its carriage");
            saved.controls.forEach((id, control) -> car.controls.put(id, Objects.requireNonNull(control)));
        }
        runtime.placement = checkpoint.placement;
        if (!runtime.placement.bodies().keySet().equals(runtime.cars.keySet())
                || !runtime.placement.bogeys().keySet().equals(runtime.bogeyOwners.keySet())
                || !runtime.placement.endpoints().keySet().equals(runtime.endpointOwners.keySet())
                || !checkpoint.bogeySpeeds.keySet().equals(runtime.bogeyOwners.keySet()))
            throw new IllegalArgumentException("Checkpoint placement does not match its definitions");
        checkpoint.bogeySpeeds.forEach((id, speed) -> { finite(speed, "bogey speed"); runtime.bogeySpeeds.put(id, speed); });
        for (Connection connection : checkpoint.connections) {
            double firstSpeed = runtime.car(runtime.owner(connection.first)).speed;
            double secondSpeed = runtime.car(runtime.owner(connection.second)).speed;
            if (Math.abs(firstSpeed - secondSpeed) > EPSILON)
                throw new IllegalArgumentException("Connected checkpoint carriages have different velocities");
            runtime.validatePair(connection.first, connection.second);
            if (endpointGap(runtime.placement, connection.first, connection.second) > PLACEMENT_TOLERANCE)
                throw new IllegalArgumentException("Checkpoint connected endpoints do not coincide");
            runtime.connections.add(connection);
        }
        for (Car car : runtime.cars.values()) {
            var reference = car.definition.bogeys().getFirst();
            close(car.distance, runtime.placement.bogeys().get(reference.id()).distance()
                    - reference.offset() - reference.mount().trackPivot().z());
        }
        // Keep the exact saved state after verification; loading must not move the carriage.
        for (Approach approach : checkpoint.approaches) {
            runtime.validatePair(approach.first, approach.second);
            runtime.validateApproachParticipants(approach.first, approach.second);
            // Restoring is not a new interaction: a near-complete request still finishes on its next tick.
            runtime.approaches.add(approach);
        }
        return runtime;
    }

    /** Semi-implicit fixed substeps; swept contact/coupling events prevent high-speed tunnelling. */
    public void tick(double seconds) {
        tick(seconds, Set.of());
    }

    /** Existing moving-block behaviors can temporarily hold their own connected component. */
    public void tick(double seconds, Set<UUID> heldCarriages) {
        tick(seconds, heldCarriages, Set.of());
    }

    /** Paused components retain velocity and commands while their backend waits for geometry. */
    public void tick(double seconds, Set<UUID> heldCarriages, Set<UUID> pausedCarriages) {
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 60)
            throw new IllegalArgumentException("Tick duration must be in (0, 60] seconds");
        Objects.requireNonNull(heldCarriages);
        Objects.requireNonNull(pausedCarriages);
        failedTravel.clear();
        var motion = new TickMotion(Set.copyOf(pausedCarriages));
        int steps = (int) Math.ceil(seconds / MAX_STEP);
        Map<UUID, Double> before = distances(placement);
        for (int i = 0; i < steps; i++) {
            step(seconds / steps, heldCarriages, motion);
            if (constraintFailure != null && constraintFailurePolicy == ConstraintFailurePolicy.STOP_ALL) break;
        }
        placement.bogeys().forEach((id, position) -> bogeySpeeds.put(id, (position.distance() - before.get(id)) / seconds));
        if (!motion.failures.isEmpty()) constraintFailure = String.join("; ", new java.util.LinkedHashSet<>(motion.failures.values()));
    }

    private void step(double dt, Set<UUID> heldCarriages, TickMotion motion) {
        constraintFailure = null;
        for (Group group : groups()) {
            if (!heldCarriages.isEmpty() && group.cars.stream().anyMatch(car -> heldCarriages.contains(car.definition.id()))) {
                group.velocity(0);
                continue;
            }
            if (motion.paused(group)) continue;
            var forces = forces(group);
            double traction = forces.values().stream().mapToDouble(f -> f.traction).sum();
            double opposition = forces.values().stream().mapToDouble(f -> Math.abs(f.brake) + Math.abs(f.resistance)).sum();
            double net = forces.values().stream().mapToDouble(Force::net).sum();
            double previous = group.speed(), next = previous + net / group.mass() * dt;
            if (previous != 0 && Math.signum(previous) != Math.signum(next)) {
                double afterStop = Math.max(0, dt - Math.abs(previous * group.mass() / net));
                next = Math.abs(traction) > opposition
                        ? Math.signum(traction) * (Math.abs(traction) - opposition) / group.mass() * afterStop : 0;
            }
            group.velocity(next);
        }
        for (Approach approach : approaches) {
            Group source = group(owner(approach.first)), target = group(owner(approach.second));
            if (!heldCarriages.isEmpty() && (source.cars.stream().anyMatch(car -> heldCarriages.contains(car.definition.id()))
                    || target.cars.stream().anyMatch(car -> heldCarriages.contains(car.definition.id())))) continue;
            if (motion.paused(source) || motion.paused(target)) continue;
            double direction = endpoint(approach.first).facing() == CouplingEndpoint.Facing.NORTH ? 1 : -1;
            source.velocity(target.speed() + direction * approach.speedMps);
        }
        // Forces and controls still update above, so applying traction wakes a stopped
        // consist immediately. An unchanged parked placement needs no constraint solve.
        if (approaches.isEmpty() && cars.values().stream().allMatch(car -> car.speed == 0)) return;
        double remaining = dt;
        int eventsLeft = Math.max(32, cars.size() * cars.size() * 4);
        while (remaining > EPSILON) {
            List<Group> groups = groups();
            Event event;
            Solution end;
            try {
                end = predicted(groups, remaining, motion);
                event = firstEvent(groups, end, remaining, motion);
            } catch (ConsistFailure failure) {
                motion.fail(failure.carriages, failure.getMessage());
                failedTravel.putAll(failure.travel);
                continue;
            } catch (VehicleConstraintSolver.ConstraintFailure failure) {
                stopForConstraint(failure.getMessage());
                return;
            }
            double travelTime = event == null ? remaining : event.time;
            // Reuse the contact sweep's end pose: ordinary motion still solves only once.
            if (travelTime == remaining) commit(end);
            else {
                try {
                    if (!move(groups, travelTime, motion)) return;
                } catch (ConsistFailure failure) {
                    motion.fail(failure.carriages, failure.getMessage());
                    failedTravel.putAll(failure.travel);
                    continue;
                }
            }
            remaining -= travelTime;
            if (event == null) break;
            if (--eventsLeft == 0) {
                if (constraintFailurePolicy == ConstraintFailurePolicy.STOP_ALL)
                    throw new IllegalStateException("Contact solver did not converge");
                failEvent(motion, event, "Contact solver did not converge");
                eventsLeft = Math.max(32, cars.size() * cars.size() * 4);
                continue;
            }
            if (event.coupling != null) {
                if (endpointGap(placement, event.coupling.first, event.coupling.second) > PLACEMENT_TOLERANCE) {
                    if (constraintFailurePolicy == ConstraintFailurePolicy.RETRY_CONSIST) {
                        failEvent(motion, event, "Manual coupling route does not produce coincident world endpoints");
                        continue;
                    }
                    constraintFailure = "Manual coupling route does not produce coincident world endpoints";
                    groups().forEach(g -> g.velocity(0));
                    approaches.removeIf(a -> a.first.equals(event.coupling.first)
                            && a.second.equals(event.coupling.second));
                    return;
                }
                try {
                    couple(event.coupling.first, event.coupling.second);
                } catch (RuntimeException failure) {
                    if (constraintFailurePolicy == ConstraintFailurePolicy.STOP_ALL) throw failure;
                    failEvent(motion, event, failureMessage(failure));
                }
            }
            else resolveContact(event.first, event.second, motion);
        }
    }

    /** Simultaneous contact is solved as one impulse, not an endless sequence of pair impulses. */
    private void resolveContact(Group first, Group second, TickMotion motion) {
        var touching = new ArrayList<Group>();
        touching.add(first); touching.add(second);
        List<Group> all = groups();
        boolean added;
        do {
            added = false;
            for (Group candidate : all) {
                if (motion.failed(candidate)) continue;
                if (touching.stream().anyMatch(g -> g.contains(candidate.cars.getFirst().definition.id()))) continue;
                double mass = touching.stream().mapToDouble(Group::mass).sum();
                double velocity = touching.stream().mapToDouble(g -> g.mass() * g.speed()).sum() / mass;
                boolean compressed = false;
                for (Group contact : touching) {
                    if (manualPair(candidate, contact)) continue;
                    for (Car a : contact.cars) for (Car b : candidate.cars) {
                        if (a.distance <= b.distance) {
                            double gap = b.distance - b.definition.rearExtent() - a.distance - a.definition.frontExtent();
                            compressed |= gap <= EPSILON && velocity >= b.speed;
                        } else {
                            double gap = a.distance - a.definition.rearExtent() - b.distance - b.definition.frontExtent();
                            compressed |= gap <= EPSILON && b.speed >= velocity;
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
        for (Approach approach : approaches) {
            if (motion.failed(group(owner(approach.first))) || motion.failed(group(owner(approach.second)))) continue;
            var coupling = new Connection(approach.first, approach.second);
            double time = couplingContactTime(coupling, groups, end, duration, false, motion);
            if (Double.isFinite(time) && time >= 0 && time <= duration && (event == null || time < event.time))
                event = new Event(time, null, null, coupling);
        }
        // Hook contact is independent of body-impact handling (Create supplies the latter).
        // Only free, approaching completion points can connect; no attraction or speed override.
        var occupied = new HashSet<UUID>();
        for (Connection connection : connections) {
            occupied.add(connection.first); occupied.add(connection.second);
        }
        var free = new ArrayList<List<CouplingEndpoint>>(groups.size());
        for (Group group : groups) {
            boolean manual = approaches.stream().anyMatch(a -> group.contains(owner(a.first)) || group.contains(owner(a.second)));
            free.add(manual || motion.failed(group) ? List.of() : group.cars.stream().flatMap(c -> c.definition.couplings().stream())
                    .filter(e -> e.kind() == CouplingEndpoint.Kind.RIGID && !occupied.contains(e.id())).toList());
        }
        for (int i = 0; i < free.size(); i++) for (int j = i + 1; j < free.size(); j++) {
            for (CouplingEndpoint a : free.get(i)) for (CouplingEndpoint b : free.get(j)) {
                boolean opposing = a.facing() == CouplingEndpoint.Facing.NORTH && b.facing() == CouplingEndpoint.Facing.SOUTH
                        || a.facing() == CouplingEndpoint.Facing.SOUTH && b.facing() == CouplingEndpoint.Facing.NORTH;
                if (!opposing) continue;
                var coupling = new Connection(a.id(), b.id());
                double time = couplingContactTime(coupling, groups, end, duration, true, motion);
                if (Double.isFinite(time) && time >= 0 && time <= duration && (event == null || time < event.time))
                    event = new Event(time, null, null, coupling);
            }
        }
        if (!collisionEnabled) return event;
        // This remains a same-route longitudinal candidate/contact model. It is not a general
        // world-space collision implementation for bodies crossing on curves or different tracks.
        for (int i = 0; i < groups.size(); i++) for (int j = i + 1; j < groups.size(); j++) {
            Group a = groups.get(i), b = groups.get(j);
            if (manualPair(a, b) || motion.failed(a) || motion.failed(b)) continue;
            for (Car ca : a.cars) for (Car cb : b.cars) {
                boolean aBehind = ca.distance <= cb.distance;
                Car behind = aBehind ? ca : cb, ahead = aBehind ? cb : ca;
                double closing = (motion.paused(aBehind ? a : b) ? 0 : behind.speed)
                        - (motion.paused(aBehind ? b : a) ? 0 : ahead.speed);
                if (closing <= EPSILON) continue;
                double gap = ahead.distance - ahead.definition.rearExtent()
                        - behind.distance - behind.definition.frontExtent();
                double time = Math.max(0, gap / closing);
                if (time <= duration && (event == null || time < event.time)) event = new Event(time, a, b, null);
            }
        }
        return event;
    }

    private double couplingContactTime(Connection coupling, List<Group> groups, Solution end, double duration,
                                       boolean natural, TickMotion motion) {
        double gap = endpointGap(placement, coupling.first, coupling.second);
        if (!natural && gap <= PLACEMENT_TOLERANCE) return 0;
        var body = placement.bodies().get(owner(coupling.first));
        var direction = body.transform(new CouplingEndpoint.Point(0, 0,
                endpoint(coupling.first).facing() == CouplingEndpoint.Facing.NORTH ? -1 : 1))
                .subtract(body.position()).normalized();
        double before = separation(placement, coupling, direction), after = separation(end, coupling, direction);
        // A freshly uncoupled pair at rest, moving together or separating must stay released.
        if (natural && after >= before - EPSILON) return Double.NaN;
        if (gap <= PLACEMENT_TOLERANCE) return 0;
        if (endpointGap(end, coupling.first, coupling.second) <= PLACEMENT_TOLERANCE) return duration;
        if (before < -PLACEMENT_TOLERANCE || after > 0) return Double.NaN;
        double low = 0, high = duration;
        Solution candidate = end;
        for (int i = 0; i < 40; i++) {
            double middle = (low + high) * .5;
            candidate = predicted(groups, middle, motion);
            if (separation(candidate, coupling, direction) > 0) low = middle;
            else high = middle;
        }
        // Crossing the same longitudinal plane is insufficient: hooks on different tracks
        // or at different heights must not latch or stop either carriage.
        if (natural && endpointGap(candidate, coupling.first, coupling.second) > PLACEMENT_TOLERANCE) return Double.NaN;
        return (low + high) * .5;
    }

    private static double separation(Solution solution, Connection coupling, TrackGeometry.Vector direction) {
        return solution.endpoints().get(coupling.second).complete()
                .subtract(solution.endpoints().get(coupling.first).complete()).dot(direction);
    }

    private boolean move(List<Group> groups, double seconds, TickMotion motion) {
        if (seconds == 0) return true;
        try {
            commit(predicted(groups, seconds, motion));
            return true;
        } catch (VehicleConstraintSolver.ConstraintFailure failure) {
            stopForConstraint(failure.getMessage());
            return false;
        }
    }

    private Solution predicted(List<Group> groups, double seconds, TickMotion motion) {
        List<Group> active = groups.stream().filter(group -> !motion.paused(group)).toList();
        if (active.isEmpty()) return placement;
        var proposed = distances(placement);
        for (Group group : active) for (Car car : group.cars) for (BogeyDefinition bogey : car.definition.bogeys())
            proposed.compute(bogey.id(), (id, distance) -> distance + group.speed() * seconds);
        try {
            // Keep the ordinary single solve. Splitting is only necessary when a backend has
            // paused part of the domain or when that aggregate solve actually fails.
            return active.size() == groups.size()
                    ? solver.solve(definitions(), connections, proposed, pins(proposed), placement)
                    : overlay(placement, solveGroups(active, proposed));
        } catch (RuntimeException failure) {
            if (constraintFailurePolicy == ConstraintFailurePolicy.STOP_ALL) throw failure;
            if (active.size() == 1) throw new ConsistFailure(active, carriageIds(active), seconds, failureMessage(failure));
            Solution result = placement;
            var failed = new HashSet<UUID>();
            String reason = failureMessage(failure);
            // All hard links remain inside their connected component; no constraint is dropped.
            // A failed component keeps its last committed pose until a later tick can solve it.
            for (Group group : active) {
                try {
                    result = overlay(result, solveGroups(List.of(group), proposed));
                } catch (RuntimeException componentFailure) {
                    failed.addAll(carriageIds(List.of(group)));
                    reason = failureMessage(componentFailure);
                }
            }
            if (!failed.isEmpty()) throw new ConsistFailure(active, failed, seconds, reason);
            return result;
        }
    }

    private Solution solveGroups(List<Group> selected, Map<UUID, Double> proposed) {
        Set<UUID> selectedIds = carriageIds(selected);
        List<CarriageDefinition> definitions = selected.stream().flatMap(group -> group.cars.stream())
                .map(car -> car.definition).toList();
        List<Connection> links = connections.stream().filter(link -> selectedIds.contains(owner(link.first))).toList();
        var pins = new LinkedHashMap<UUID, Double>();
        for (Group group : selected) {
            UUID reference = group.cars.getFirst().definition.referenceBogey().id();
            pins.put(reference, proposed.get(reference));
        }
        return solver.solve(definitions, links, proposed, pins, placement);
    }

    private static Solution overlay(Solution base, Solution update) {
        var bodies = new LinkedHashMap<>(base.bodies()); bodies.putAll(update.bodies());
        var bogeys = new LinkedHashMap<>(base.bogeys()); bogeys.putAll(update.bogeys());
        var endpoints = new LinkedHashMap<>(base.endpoints()); endpoints.putAll(update.endpoints());
        return new Solution(bodies, bogeys, endpoints);
    }

    private static Set<UUID> carriageIds(List<Group> groups) {
        var ids = new HashSet<UUID>();
        groups.forEach(group -> group.cars.forEach(car -> ids.add(car.definition.id())));
        return ids;
    }

    private void failEvent(TickMotion motion, Event event, String reason) {
        var affected = event.coupling == null ? List.of(event.first, event.second)
                : List.of(group(owner(event.coupling.first)), group(owner(event.coupling.second)));
        motion.fail(carriageIds(affected), reason);
    }

    private static String failureMessage(RuntimeException failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private Map<UUID, Double> pins(Map<UUID, Double> proposed) {
        var pins = new LinkedHashMap<UUID, Double>();
        for (Group group : groups()) {
            UUID reference = group.cars.getFirst().definition.referenceBogey().id();
            pins.put(reference, proposed.get(reference));
        }
        return pins;
    }

    private void commit(Solution solved) {
        placement = solved;
        for (Car car : cars.values()) {
            var reference = car.definition.bogeys().getFirst();
            // A diagnostic reference coordinate, not the world position of the carriage origin.
            car.distance = solved.bogeys().get(reference.id()).distance() - reference.offset()
                    - reference.mount().trackPivot().z();
        }
        constraintFailure = null;
    }

    private void stopForConstraint(String reason) {
        constraintFailure = reason;
        groups().forEach(g -> g.velocity(0));
    }

    private static Map<UUID, Double> distances(Solution solution) {
        var result = new LinkedHashMap<UUID, Double>();
        solution.bogeys().forEach((id, position) -> result.put(id, position.distance()));
        return result;
    }

    private static double endpointGap(Solution solution, UUID first, UUID second) {
        var delta = solution.endpoints().get(first).complete().subtract(solution.endpoints().get(second).complete());
        return Math.sqrt(delta.dot(delta));
    }

    private static void requireSamePlacement(Solution expected, Solution actual) {
        requireSamePlacement(expected, actual, true);
    }

    private static void requireSamePlacement(Solution expected, Solution actual, boolean endpointsUnchanged) {
        if (!expected.bodies().keySet().equals(actual.bodies().keySet())
                || !expected.bogeys().keySet().equals(actual.bogeys().keySet())
                || !expected.endpoints().keySet().equals(actual.endpoints().keySet()))
            throw new IllegalArgumentException("Solved placement identities changed");
        expected.bogeys().forEach((id, before) -> {
            var after = actual.bogeys().get(id);
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
        expected.bodies().forEach((id, before) -> {
            var after = actual.bodies().get(id);
            close(before.position(), after.position());
            close(Math.IEEEremainder(before.yaw() - after.yaw(), Math.TAU), 0);
            close(Math.IEEEremainder(before.pitch() - after.pitch(), Math.TAU), 0);
            close(Math.IEEEremainder(before.roll() - after.roll(), Math.TAU), 0);
        });
        if (endpointsUnchanged)
            expected.endpoints().forEach((id, before) -> {
                var after = actual.endpoints().get(id);
                close(before.pivot(), after.pivot()); close(before.start(), after.start()); close(before.complete(), after.complete());
            });
    }

    private static void close(TrackGeometry.Vector first, TrackGeometry.Vector second) {
        var difference = first.subtract(second);
        close(Math.sqrt(difference.dot(difference)), 0);
    }

    private static void close(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second) || Math.abs(first - second) > PLACEMENT_TOLERANCE)
            throw new IllegalArgumentException("Checkpoint or connection would displace the solved geometry");
    }

    private Map<UUID, Force> forces(Group group) {
        double traction = 0, opposition = 0;
        for (Car car : group.cars) for (BogeyDefinition bogey : car.definition.bogeys()) {
            Control control = car.controls.get(bogey.id());
            traction += control.brake > 0 ? 0 : control.throttle * bogey.parameters().tractionForceN();
            opposition += control.brake * bogey.parameters().brakeForceN() + bogey.parameters().rollingResistanceN();
        }
        double direction = Math.signum(group.speed() == 0 ? traction : group.speed());
        double scale = group.speed() == 0 && opposition > 0 ? Math.min(1, Math.abs(traction) / opposition) : 1;
        var result = new HashMap<UUID, Force>();
        for (Car car : group.cars) for (BogeyDefinition bogey : car.definition.bogeys()) {
            BogeyParameters parameters = bogey.parameters();
            Control control = car.controls.get(bogey.id());
            double drive = control.brake > 0 ? 0 : control.throttle * parameters.tractionForceN();
            double brake = -direction * control.brake * parameters.brakeForceN() * scale;
            double resistance = -direction * parameters.rollingResistanceN() * scale;
            DriveMode mode = control.brake > 0 ? DriveMode.BRAKING
                    : drive != 0 ? DriveMode.TRACTION : DriveMode.COASTING;
            result.put(bogey.id(), new Force(drive, brake, resistance,
                    mode == DriveMode.BRAKING ? control.brake : mode == DriveMode.TRACTION ? Math.abs(control.throttle) : 0, mode));
        }
        return result;
    }

    private void validatePair(UUID first, UUID second) {
        CouplingEndpoint a = endpoint(first), b = endpoint(second);
        if (first.equals(second) || group(owner(first)).contains(owner(second)))
            throw new IllegalArgumentException("Coupling endpoints must belong to separate consists");
        if (connections.stream().anyMatch(c -> c.first.equals(first) || c.second.equals(first)
                || c.first.equals(second) || c.second.equals(second))) throw new IllegalStateException("Occupied coupling endpoint");
        if (a.kind() != CouplingEndpoint.Kind.RIGID || b.kind() != CouplingEndpoint.Kind.RIGID)
            throw new UnsupportedOperationException("Articulated constraints require their own animation/constraint adapter");
        boolean opposing = a.facing() == CouplingEndpoint.Facing.NORTH && b.facing() == CouplingEndpoint.Facing.SOUTH
                || a.facing() == CouplingEndpoint.Facing.SOUTH && b.facing() == CouplingEndpoint.Facing.NORTH;
        if (!opposing)
            throw new IllegalArgumentException("Endpoints are not aligned in this route frame");
    }

    private boolean manualPair(Group a, Group b) {
        return approaches.stream().anyMatch(p -> a.contains(owner(p.first)) && b.contains(owner(p.second))
                || b.contains(owner(p.first)) && a.contains(owner(p.second)));
    }

    private boolean involved(Approach approach, UUID car) {
        return group(owner(approach.first)).contains(car) || group(owner(approach.second)).contains(car);
    }

    private List<Group> groups() {
        var result = new ArrayList<Group>();
        var assigned = new HashSet<UUID>();
        for (UUID id : cars.keySet()) {
            if (assigned.contains(id)) continue;
            Group group = group(id);
            group.cars.forEach(c -> assigned.add(c.definition.id()));
            result.add(group);
        }
        return result;
    }

    private Group group(UUID id) {
        car(id);
        Set<UUID> ids = new HashSet<>();
        ids.add(id);
        boolean changed;
        do {
            changed = false;
            for (Connection c : connections) {
                UUID first = owner(c.first), second = owner(c.second);
                if (ids.contains(first) || ids.contains(second)) {
                    changed |= ids.add(first); changed |= ids.add(second);
                }
            }
        } while (changed);
        return new Group(cars.values().stream().filter(c -> ids.contains(c.definition.id())).toList());
    }

    private UUID owner(UUID endpoint) {
        UUID id = endpointOwners.get(endpoint);
        if (id == null) throw new IllegalArgumentException("Unknown coupling endpoint " + endpoint);
        return id;
    }
    private CouplingEndpoint endpoint(UUID id) {
        return car(owner(id)).definition.couplings().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }
    private Car car(UUID id) {
        Car car = cars.get(id);
        if (car == null) throw new IllegalArgumentException("Unknown carriage " + id);
        return car;
    }
    private static double momentumVelocity(Group a, Group b) {
        return (a.mass() * a.speed() + b.mass() * b.speed()) / (a.mass() + b.mass());
    }
    private static void finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name);
    }
    private record Force(double traction, double brake, double resistance, double load, DriveMode mode) {
        double net() { return traction + brake + resistance; }
    }
    private record Event(double time, Group first, Group second, Connection coupling) { }
    private static final class TickMotion {
        final Set<UUID> paused;
        final Map<UUID, String> failures = new LinkedHashMap<>();
        TickMotion(Set<UUID> paused) { this.paused = paused; }
        boolean paused(Group group) {
            return group.cars.stream().anyMatch(car -> paused.contains(car.definition.id()) || failures.containsKey(car.definition.id()));
        }
        boolean failed(Group group) {
            return group.cars.stream().anyMatch(car -> failures.containsKey(car.definition.id()));
        }
        void fail(Set<UUID> carriages, String reason) { carriages.forEach(id -> failures.putIfAbsent(id, reason)); }
    }
    private static final class ConsistFailure extends RuntimeException {
        final Set<UUID> carriages;
        final Map<UUID, Double> travel = new HashMap<>();
        ConsistFailure(List<Group> groups, Set<UUID> carriages, double seconds, String reason) {
            super(reason);
            this.carriages = carriages;
            for (Group group : groups) for (Car car : group.cars)
                if (carriages.contains(car.definition.id())) travel.put(car.definition.id(), group.speed() * seconds);
        }
    }
    private record Group(List<Car> cars) {
        double mass() { return cars.stream().mapToDouble(c -> c.definition.massKg()).sum(); }
        double speed() { return cars.getFirst().speed; }
        void velocity(double velocity) { cars.forEach(c -> c.speed = velocity); }
        boolean contains(UUID id) { return cars.stream().anyMatch(c -> c.definition.id().equals(id)); }
    }
    private static final class Car {
        final CarriageDefinition definition;
        final Map<UUID, Control> controls = new LinkedHashMap<>();
        double distance, speed;
        Car(CarriageDefinition definition, double distance, double speed) {
            this.definition = definition; this.distance = distance; this.speed = speed;
            definition.bogeys().forEach(b -> controls.put(b.id(), Control.NEUTRAL));
        }
    }
}
