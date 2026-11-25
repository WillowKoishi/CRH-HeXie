package willow.train.hexie.vehicle.runtime;

// split from willow/train/hexie/vehicle/runtime/VehicleRuntime.java

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
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.railway.TrackPathView;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.vehicle.CouplingEndpointView;
import willow.train.hexie.bridge.vehicle.VehicleBridge;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.core.api.traincontrol.TrainCommand;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver.Solution;
import willow.train.hexie.vehicle.runtime.VehicleRuntimeDynamics.Force;
import willow.train.hexie.vehicle.runtime.VehicleRuntimeDynamics.Prediction;
import willow.train.hexie.vehicle.runtime.VehicleRuntimeDynamics.TickMotion;

/**
 * A server-owned route domain. Only put carriages on the same comparable route
 * in one runtime. Independently solved bogey positions and rigid body poses are authoritative. No world
 * blocks, Create control, rendering or external physics are owned here. One block equals one metre.
 * Each bogey contributes its registered supported mass and forces. Linked cars share momentum;
 * touching unlinked cars exchange a non-rebounding contact impulse without becoming linked.
 */
public final class VehicleRuntime implements VehicleRuntimeView {
    private final VehicleRouteTransfers transfers = new VehicleRouteTransfers(this);
    private final VehicleRuntimeCheckpoints checkpoints = new VehicleRuntimeCheckpoints(this);
    private final VehicleRuntimeCouplings couplings = new VehicleRuntimeCouplings(this);
    final VehicleRuntimeDynamics dynamics = new VehicleRuntimeDynamics(this);
    static final double EPSILON = 1e-8;
    static final double PLACEMENT_TOLERANCE = 1e-5;

    final Map<UUID, Car> cars = new LinkedHashMap<>();
    final Map<UUID, UUID> bogeyOwners = new HashMap<>();
    final Map<UUID, UUID> endpointOwners = new HashMap<>();
    final List<Connection> connections = new ArrayList<>();
    final List<Approach> approaches = new ArrayList<>();
    final List<CouplingControlReset> couplingControlResets = new ArrayList<>();
    final Map<UUID, Double> bogeySpeeds = new LinkedHashMap<>();
    // Dynamics owns these cursors. A failed pose evaluation must not rewind the next step.
    final Map<UUID, Double> motionDistances = new LinkedHashMap<>();
    final Map<UUID, Double> failedTravel = new HashMap<>();
    final TrackPathView path;
    final VehicleConstraintSolver solver;
    Solution placement = new Solution(Map.of(), Map.of(), Map.of());
    String constraintFailure;

    long consistRevision;

    private long capabilityRevision = -1;
    private final Map<UUID, ConsistCapabilities> capabilities = new HashMap<>();

    public VehicleRuntime() { this(TrackPathView.straight()); }
    public VehicleRuntime(TrackPathView path) {
        this.path = Objects.requireNonNull(path);
        solver = new VehicleConstraintSolver(path);
    }
    public Solution placement() { return placement; }
    public CarriageDefinition definition(UUID id) { return car(id).definition; }
    /** Upgrade legacy saved endpoints once; this changes only captured model metadata. */
    public void captureCouplerVisual(UUID id, CouplingBridge.CouplingEndpointVisualView visual) {
        couplings.captureCouplerVisual(id, (CouplingEndpoint.Visual) visual);
    }
    /** Explicit appearance edits preserve coupling geometry, adapters, connections and solved poses. */
    public boolean updateCouplerVisual(UUID id, CouplingBridge.CouplingEndpointVisualView visual) {
        return couplings.updateCouplerVisual(id, (CouplingEndpoint.Visual) visual);
    }
    public double bogeySpeed(UUID id) { return Objects.requireNonNull(bogeySpeeds.get(id), "Unknown bogey"); }
    public double motionDistance(UUID bogey) { return Objects.requireNonNull(motionDistances.get(bogey), "Unknown bogey"); }
    public Optional<String> constraintFailure() { return Optional.ofNullable(constraintFailure); }
    public boolean collisionEnabled() {
        return dynamics.collisionEnabled();
    }
    /** Backend-supplied policy; never changes saved bogey specifications. */
    public void dynamicsMultipliers(double acceleration, double braking) {
        dynamics.dynamicsMultipliers(acceleration, braking);
    }
    public double brakingMultiplier() {
        return dynamics.brakingMultiplier();
    }

    public void poweredCarriages(Set<UUID> carriages, double multiplier) {
        dynamics.poweredCarriages(carriages, multiplier);
    }

    double wheelPowerMultiplier(UUID carriage) {
        return dynamics.wheelPowerMultiplier(carriage);
    }
    /** Backend policy: Create owns body impacts; manual and contact coupling remain active. */
    public void collisionEnabled(boolean enabled) {
        dynamics.collisionEnabled(enabled);
    }
    public ConstraintFailurePolicy constraintFailurePolicy() {
        return dynamics.constraintFailurePolicy();
    }
    /** Legacy API; pose failure handling is identical with and without a compatibility backend. */
    public void constraintFailurePolicy(ConstraintFailurePolicy policy) {
        dynamics.constraintFailurePolicy(policy);
    }
    /** Changes for membership, connections and installed hook definitions, never for motion or controls. */
    public long consistRevision() { return consistRevision; }

    /** Constant-time per-hook animation lookup, rebuilt only when independent topology changes. */
    public UUID couplingPartner(UUID endpoint) {
        return couplings.couplingPartner(endpoint);
    }

    public static final class RouteMismatchException extends VehicleBridge.VehicleRuntimeRouteMismatchExceptionView {
        RouteMismatchException(RuntimeException cause) {
            super("Saved track geometry no longer matches its route");
            initCause(cause);
        }
    }

    public enum ConstraintFailurePolicy { STOP_ALL, RETRY_CONSIST }

    /** Signed wheel RPM, absolute drive RPM and [0,1] commanded drive/braking load for sound. */
    public record BogeyState(UUID id, String modelId, double distance, double supportedMassKg,
                             double tractionForceN, double brakeForceN, double resistanceForceN,
                             double wheelRpm, double driveRpm, double load, VehicleBridge.VehicleRuntimeDriveMode mode) implements VehicleBridge.VehicleRuntimeBogeyStateView {
        public double netForceN() { return tractionForceN + brakeForceN + resistanceForceN; }
    }

    public record CarriageState(UUID id, double distance, double speed, double massKg,
                                List<BogeyState> bogeys, List<UUID> consist) implements VehicleBridge.VehicleRuntimeCarriageStateView {
        public CarriageState { bogeys = List.copyOf(bogeys); consist = List.copyOf(consist); }
    }

    public record Connection(UUID first, UUID second) implements VehicleBridge.VehicleRuntimeConnectionView {
        public Connection { Objects.requireNonNull(first); Objects.requireNonNull(second); }
    }

    public record Approach(UUID first, UUID second, double speedMps) implements VehicleBridge.VehicleRuntimeApproachView {
        public Approach {
            Objects.requireNonNull(first); Objects.requireNonNull(second);
            if (!Double.isFinite(speedMps) || speedMps <= 0) throw new IllegalArgumentException("Approach speed");
        }
    }

    public record CouplingState(UUID first, UUID second, double progress, boolean connected,
                                CouplingBridge.CouplingEndpointKind kind, double extension,
                                CouplerAngles firstAngles, CouplerAngles secondAngles) implements VehicleBridge.VehicleRuntimeCouplingStateView {
        public CouplingState(UUID first, UUID second, double progress, boolean connected, CouplingBridge.CouplingEndpointKind kind) {
            this(first, second, progress, connected, kind, 0, CouplerAngles.ZERO, CouplerAngles.ZERO);
        }
    }

    public record SavedCarriage(CarriageDefinition definition, double distance, double speed,
                                Map<UUID, TrainCommand> controls) implements VehicleBridge.VehicleRuntimeSavedCarriageView {
        public SavedCarriage { Objects.requireNonNull(definition); controls = Map.copyOf(controls); }
    }

    public record Checkpoint(List<SavedCarriage> carriages, List<Connection> connections,
                             List<Approach> approaches, Solution placement, Map<UUID, Double> bogeySpeeds,
                             Map<UUID, Double> motionDistances, boolean legacyCouplerAxes) implements VehicleBridge.VehicleRuntimeCheckpointView {
        public Checkpoint(List<SavedCarriage> carriages, List<Connection> connections,
                          List<Approach> approaches, Solution placement, Map<UUID, Double> bogeySpeeds,
                          Map<UUID, Double> motionDistances) {
            this(carriages, connections, approaches, placement, bogeySpeeds, motionDistances, false);
        }
        public Checkpoint(List<SavedCarriage> carriages, List<Connection> connections,
                          List<Approach> approaches, Solution placement, Map<UUID, Double> bogeySpeeds) {
            this(carriages, connections, approaches, placement, bogeySpeeds, distances(placement));
        }
        public Checkpoint {
            carriages = List.copyOf(carriages);
            connections = List.copyOf(connections);
            approaches = List.copyOf(approaches);
            Objects.requireNonNull(placement);
            bogeySpeeds = Map.copyOf(bogeySpeeds);
            motionDistances = Map.copyOf(motionDistances);
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

    void installCarriage(CarriageDefinition definition, double distance, double speed) {
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
        if (group(id).cars().size() != 1 || approaches.stream().anyMatch(a -> involved(a, id)))
            throw new IllegalStateException("Uncouple and cancel manual coupling before removing a carriage");
        cars.remove(id);
        car.definition.bogeys().forEach(b -> bogeyOwners.remove(b.id()));
        car.definition.couplings().forEach(e -> endpointOwners.remove(e.id()));
        var bodies = new LinkedHashMap<>(placement.bodyPoses());
        var bogeys = new LinkedHashMap<>(placement.bogeyPositions());
        var endpoints = new LinkedHashMap<>(placement.endpointPoses());
        bodies.remove(id);
        car.definition.bogeys().forEach(b -> { bogeys.remove(b.id()); bogeySpeeds.remove(b.id()); motionDistances.remove(b.id()); });
        car.definition.couplings().forEach(e -> endpoints.remove(e.id()));
        placement = new Solution(bodies, bogeys, endpoints);
        consistRevision++;
    }

    public List<CarriageDefinition> definitions() { return cars.values().stream().map(c -> c.definition).toList(); }

    /** Entity/control adapters need velocity without rebuilding diagnostic force/consist reports. */
    public double speed(UUID carriage) { return car(carriage).speed; }
    public record ConsistCapabilities(double massKg, double maximumTractionN, double maximumPowerW, double maximumBrakeForceN) {}
    /** Definitions are immutable; only topology/definition changes invalidate the shared capability totals. */
    public ConsistCapabilities capabilities(UUID carriage) {
        if (capabilityRevision != consistRevision) {
            capabilities.clear();
            for (var group : groups()) {
                double mass = 0, traction = 0, power = 0, brake = 0;
                for (var member : group.cars()) {
                    mass += member.definition.massKg();
                    for (var bogey : member.definition.bogeys()) {
                        double available = bogey.parameters().availableTractionN(0);
                        traction += available;
                        if (available > 0) power = Math.min(Double.MAX_VALUE, power + bogey.parameters().tractionPowerW());
                        brake += bogey.parameters().brakeForceN();
                    }
                }
                var value = new ConsistCapabilities(mass, traction, power, brake);
                for (var member : group.cars()) capabilities.put(member.definition.id(), value);
            }
            capabilityRevision = consistRevision;
        }
        return Objects.requireNonNull(capabilities.get(carriage), "Unknown carriage");
    }
    /** One inexpensive control/display projection; does not rebuild per-bogey diagnostic forces. */
    public record ConsistReadings(double speed, double massKg, double maximumBrakeForceN,
                                  double traction, double brake, double brakingMultiplier) {
        public double serviceDeceleration(double serviceLimit) {
            return massKg > 0 ? maximumBrakeForceN / massKg * brakingMultiplier * serviceLimit : 0;
        }
    }
    public ConsistReadings consistReadings(UUID carriage) {
        var group = group(carriage);
        var capacity = capabilities(carriage);
        double traction = 0, brake = 0;
        for (Car member : group.cars()) {
            for (BogeyDefinition bogey : member.definition.bogeys()) {
                var control = member.controls.get(bogey.id());
                brake = Math.max(brake, control.brake());
                if (bogey.parameters().availableTractionN(group.speed()) > 0)
                    traction = Math.max(traction, Math.abs(control.throttle()));
            }
        }
        return new ConsistReadings(group.speed(), capacity.massKg(), capacity.maximumBrakeForceN(), traction, brake, dynamics.brakingMultiplier);
    }
    /** Requested displacement of this tick's rejected prediction, for backend-native route checks. */
    public double failedTravel(UUID carriage) {
        return dynamics.failedTravel(carriage);
    }

    /** Apply a backend's native velocity response to exactly one connected component. */
    public void speedForConsist(UUID carriage, double speed) {
        dynamics.speedForConsist(carriage, speed);
    }

    /** Direction requested by the connected bogeys before a stopped train has advanced. */
    public double tractionDirection(UUID carriage) {
        double traction = 0;
        var group = group(carriage);
        for (Car member : group.cars()) for (BogeyDefinition bogey : member.definition.bogeys()) {
            TrainCommand control = member.controls.get(bogey.id());
            traction += control.throttle() * bogey.availableTractionN(group.speed(),
                    wheelPowerMultiplier(member.definition.id()));
        }
        return Math.signum(traction);
    }

    public void controlsForConsist(UUID carriage, TrainCommand control) {
        Objects.requireNonNull(control);
        for (Car member : group(carriage).cars()) member.controls.replaceAll((id, previous) -> control);
    }

    public boolean connected(UUID first, UUID second) {
        return couplings.connected(first, second);
    }

    /** Backend disappearance is a stopped runtime, never permission to use stale track samples. */
    public void halt(String reason) {
        constraintFailure = Objects.requireNonNull(reason);
        groups().forEach(g -> g.velocity(0));
        cars.values().forEach(car -> car.controls.replaceAll((id, previous) -> TrainCommand.NEUTRAL));
        bogeySpeeds.replaceAll((id, previous) -> 0.0);
    }

    public void controls(UUID carriage, TrainCommand control) {
        Objects.requireNonNull(control);
        car(carriage).controls.replaceAll((id, previous) -> control);
    }

    public void controlBogey(UUID bogey, TrainCommand control) {
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
            bogeys.add(new BogeyState(bogey.id(), bogey.modelId(), placement.bogeyPositions().get(bogey.id()).distance(),
                    bogey.parameters().loadKg(), force.traction(), force.brake(), force.resistance(),
                    rpm, Math.abs(rpm) * bogey.parameters().driveRatio(), force.load(), force.mode()));
        }
        return new CarriageState(id, car.distance, car.speed, car.definition.massKg(), bogeys,
                group.cars().stream().map(c -> c.definition.id()).toList());
    }

    public List<CarriageState> states() { return cars.keySet().stream().map(this::state).toList(); }

    /** A completed connection resets its joined controls once; route-domain merges do not. */
    public record CouplingControlReset(List<UUID> carriages, double brake) implements VehicleBridge.VehicleRuntimeCouplingControlResetView {
        public CouplingControlReset { carriages = List.copyOf(carriages); }
    }

    /** The owning adapter consumes these events to update its physical driving desks. */
    public List<CouplingControlReset> drainCouplingControlResets() {
        return couplings.drainCouplingControlResets();
    }

    public void couplingAvailability(java.util.function.Predicate<CouplingEndpointView> available) {
        couplings.couplingAvailability(available);
    }
    public boolean couplingAvailable(CouplingEndpoint endpoint) {
        return couplings.couplingAvailable(endpoint);
    }

    /** The registered completion regions must meet before the connected constraint is installed. */
    public void couple(UUID first, UUID second) {
        couplings.couple(first, second);
    }

    public void uncouple(UUID endpoint) {
        couplings.uncouple(endpoint);
    }

    /** Explicit shunting request: the first car and its consist approach the second endpoint. */
    public void beginCoupling(UUID first, UUID second, double approachSpeedMps) {
        couplings.beginCoupling(first, second, approachSpeedMps);
    }

    void validateApproachParticipants(UUID first, UUID second) {
        couplings.validateApproachParticipants(first, second);
    }

    public void cancelCoupling(UUID endpoint) {
        couplings.cancelCoupling(endpoint);
    }

    /** Reconcile persisted block-owned release conditions before the next motion step. */
    public void releaseCouplingsIf(java.util.function.Predicate<CouplingEndpointView> shouldRelease) {
        couplings.releaseCouplingsIf(shouldRelease);
    }

    /** Install on a free hook without moving its body, bogeys or independent dynamics cursors. */
    public void installCouplerAdapter(UUID endpointId, CouplingBridge.CouplerAdapterView adapter) {
        couplings.installCouplerAdapter(endpointId, (CouplerAdapter) adapter);
    }

    public CouplerAdapter removeCouplerAdapter(UUID endpointId) {
        return couplings.removeCouplerAdapter(endpointId);
    }

    /** Adjust the body floor without changing rail cursors, speed, bogeys or connected hooks. */
    public void setFloorOffset(UUID carriageId, double offset) {
        Car car = car(carriageId);
        CarriageDefinition old = car.definition;
        CarriageDefinition adjusted = old.withFloorOffset(offset);
        if (adjusted == old) return;
        var body = placement.bodyPoses().get(carriageId);
        var shift = body.rotate(new Point3(0, offset - old.floorOffset(), 0));
        var bodies = new LinkedHashMap<>(placement.bodyPoses());
        bodies.put(carriageId, new VehicleConstraintSolver.BodyPose(body.position().subtract(shift.scale(-1)),
                body.yaw(), body.pitch(), body.roll()));
        car.definition = adjusted;
        placement = new Solution(bodies, placement.bogeyPositions(), placement.endpointPoses(), placement.brokenConnections());
    }

    /** Move one real support mount; fixed block/collision coordinates stay in the body frame. */
    public void shiftBogeyMount(UUID carriageId, int index, Point3 shift) {
        var car = car(carriageId);
        var old = car.definition;
        var bogeys = new ArrayList<>(old.bogeys());
        var previous = bogeys.get(index);
        var mount = previous.mount();
        var point = mount.bodyPoint().plus(shift);
        bogeys.set(index, new BogeyDefinition(previous.id(), previous.modelId(), -point.z(),
                previous.geometry(), previous.parameters(), new BogeyMount(point, mount.trackPivot(), mount.type(), mount.lateralLimit())));
        var updated = new CarriageDefinition(old.id(), bogeys, old.couplings(), old.rearExtent(), old.frontExtent(), old.floorOffset());
        var proposed = new LinkedHashMap<>(motionDistances);
        proposed.compute(previous.id(), (id, distance) -> distance - shift.z());
        car.definition = updated;
        try {
            var solved = solver.solve(definitions(), connections, proposed, pins(proposed), placement);
            if (!solved.brokenConnections().isEmpty()) throw new IllegalArgumentException("Mount adjustment would break a coupling");
            commit(solved);
            motionDistances.clear(); motionDistances.putAll(distances(solved));
            consistRevision++;
        } catch (RuntimeException failure) { car.definition = old; throw failure; }
    }

    public List<CouplingState> couplingStates() {
        return couplings.couplingStates();
    }

    public Checkpoint checkpoint() {
        return checkpoints.checkpoint();
    }

    /**
     * Replace an extended route whose existing geometry is unchanged. Only the route coordinate
     * origin moves: preserve the exact solved body/bogey/endpoint poses and ongoing controls.
     * Restore validates the new path at every support before this replacement can be installed.
     */
    public VehicleRuntime rebind(TrackPathView target, double distanceOffset) {
        return transfers.rebind(target, distanceOffset);
    }

    /** One-time backend geometry upgrade, mapping each saved support by its original edge parameter. */
    public VehicleRuntime remapRoute(TrackPathView target, java.util.function.DoubleUnaryOperator mapDistance) {
        return transfers.remapRoute(target, mapDistance);
    }

    /**
     * Combine two already aligned route domains without moving their world geometry or connecting
     * them automatically. The target keeps the first domain's direction. A reversed second domain
     * changes its normalized local frame by a half turn; adapters must likewise reverse each
     * affected carriage's captured-model basis. Source checkpoints remain untouched.
     */
    public static VehicleRuntime merge(TrackPathView target, Checkpoint first, double firstOffset,
                                        Checkpoint second, double secondOffset, boolean reverseSecond) {
        return VehicleRouteTransfers.merge(target, first, firstOffset, second, secondOffset, reverseSecond);
    }

    /**
     * Stop one connected consist at a backend signal/station point. The fixed reference bogey
     * remains the reference: a bounded scalar correction finds its distance which places the
     * requested (possibly different) bogey at the point. Other consists and all controls survive.
     */
    public void stopAtTrackPoint(UUID member, UUID bogeyId, double exactBogeyDistance) {
        dynamics.stopAtTrackPoint(member, bogeyId, exactBogeyDistance);
    }

    /**
     * Rebuild this whole route domain against explicitly mapped carriage reference distances.
     * No state is changed on this runtime if validation/placement fails. Definitions, hard links
     * and pending approach requests survive; approach progress follows the new endpoint geometry.
     */
    public VehicleRuntime rebase(TrackPathView target, Map<UUID, Double> carriageDistances, boolean stop) {
        return transfers.rebase(target, carriageDistances, stop);
    }

    /** Reposition with one explicit velocity for the whole domain; clear prior driver commands. */
    public VehicleRuntime rebase(TrackPathView target, Map<UUID, Double> carriageDistances, double speed) {
        return transfers.rebase(target, carriageDistances, speed);
    }

    /** Recover complete consists on a new route even if the saved route is unavailable. */
    public static VehicleRuntime relocateCheckpoint(Checkpoint source, TrackPathView target,
                                                     Map<UUID, Double> carriageDistances) {
        return VehicleRouteTransfers.relocateCheckpoint(source, target, carriageDistances);
    }

    /**
     * Copy whole connected components for a backend/domain split. A hard link may not be cut.
     * Manual approaches crossing this boundary are cancelled in the copy, never converted to links.
     * The caller retains/replaces the complementary domain explicitly; this method removes no cars.
     */
    public VehicleRuntime subset(Set<UUID> carriageIds) {
        return transfers.subset(carriageIds);
    }

    /**
     * Retain complete live consists without resampling their track. Removing other consists
     * changes no solved pose; the external route may be unavailable until the next rebind.
     * Saved-state restoration still checks ownership, connections, controls and cursors.
     */
    public VehicleRuntime subsetPreservingPlacement(Set<UUID> carriageIds) {
        return transfers.subsetPreservingPlacement(carriageIds);
    }

    /** Select saved complete consists without resolving or resampling their previous route. */
    public static Checkpoint subsetCheckpoint(Checkpoint source, Set<UUID> carriageIds) {
        return VehicleRuntimeCheckpoints.subsetCheckpoint(source, carriageIds);
    }

    public static VehicleRuntime restore(Checkpoint checkpoint) {
        return VehicleRuntimeCheckpoints.restore(checkpoint);
    }

    public static VehicleRuntime restore(Checkpoint checkpoint, TrackPathView path) {
        return VehicleRuntimeCheckpoints.restore(checkpoint, path);
    }

    /** One-time upgrade of snapshots written before rigid end-support constraints were enforced. */
    public static VehicleRuntime restoreLegacyPlacement(Checkpoint checkpoint, TrackPathView path) {
        return VehicleRuntimeCheckpoints.restoreLegacyPlacement(checkpoint, path);
    }

    static VehicleRuntime restore(Checkpoint checkpoint, TrackPathView path, boolean upgradePlacement) {
        return VehicleRuntimeCheckpoints.restore(checkpoint, path, upgradePlacement);
    }

    /** Restore and validate saved data independently from availability of the old track geometry. */
    static VehicleRuntime restoreSavedState(Checkpoint checkpoint, TrackPathView path) {
        return VehicleRuntimeCheckpoints.restoreSavedState(checkpoint, path);
    }

    /** Semi-implicit fixed substeps; swept contact/coupling events prevent high-speed tunnelling. */
    public void tick(double seconds) {
        dynamics.tick(seconds);
    }

    /** Existing moving-block behaviors can temporarily hold their own connected component. */
    public void tick(double seconds, Set<UUID> heldCarriages) {
        dynamics.tick(seconds, heldCarriages);
    }

    /** Paused components retain velocity and commands while their backend waits for geometry. */
    public void tick(double seconds, Set<UUID> heldCarriages, Set<UUID> pausedCarriages) {
        dynamics.tick(seconds, heldCarriages, pausedCarriages);
    }

    TrackGeometryView.Vector couplingDirection(Solution solution, UUID id) {
        return couplings.couplingDirection(solution, id);
    }

    boolean regionsMeet(Solution solution, UUID first, UUID second, boolean engagement) {
        return couplings.regionsMeet(solution, first, second, engagement);
    }

    static double separation(Solution solution, Connection coupling, TrackGeometryView.Vector direction) {
        return VehicleRuntimeCouplings.separation(solution, coupling, direction);
    }

    void commit(Prediction prediction, TickMotion motion, List<Group> groups, double seconds) {
        dynamics.commit(prediction, motion, groups, seconds);
    }

    Solution solveGroups(List<Group> selected, Map<UUID, Double> proposed) {
        return dynamics.solveGroups(selected, proposed);
    }

    static Solution overlay(Solution base, Solution update) {
        return VehicleRuntimeDynamics.overlay(base, update);
    }

    Map<UUID, Double> pins(Map<UUID, Double> proposed) {
        var pins = new LinkedHashMap<UUID, Double>();
        for (Group group : groups()) {
            UUID reference = group.cars().getFirst().definition.referenceBogey().id();
            pins.put(reference, proposed.get(reference));
        }
        return pins;
    }

    void commit(Solution solved) {
        Solution previous = placement;
        // The solver reports unavailable connections; only this independent topology owner removes them.
        if (!solved.brokenConnections().isEmpty()) {
            if (connections.removeAll(solved.brokenConnections())) consistRevision++;
            placement = new Solution(solved.bodyPoses(), solved.bogeyPositions(), solved.endpointPoses());
        } else placement = solved;
        solved.bogeyPositions().forEach((id, position) -> {
            if (position != previous.bogeyPositions().get(id)) motionDistances.put(id, position.distance());
        });
        for (Car car : cars.values()) {
            var reference = car.definition.bogeys().getFirst();
            // A diagnostic reference coordinate, not the world position of the carriage origin.
            car.distance = solved.bogeyPositions().get(reference.id()).distance() - reference.offset()
                    - reference.mount().trackPivot().z();
        }
        constraintFailure = null;
    }

    static Map<UUID, Double> distances(Solution solution) {
        var result = new LinkedHashMap<UUID, Double>();
        solution.bogeyPositions().forEach((id, position) -> result.put(id, position.distance()));
        return result;
    }

    static double endpointGap(Solution solution, UUID first, UUID second) {
        return VehicleRuntimeCouplings.endpointGap(solution, first, second);
    }

    Map<UUID, Force> forces(Group group) {
        return dynamics.forces(group);
    }

    void validatePair(UUID first, UUID second) {
        couplings.validatePair(first, second);
    }

    boolean manualPair(Group a, Group b) {
        return couplings.manualPair(a, b);
    }

    boolean involved(Approach approach, UUID car) {
        return couplings.involved(approach, car);
    }

    List<Group> groups() {
        var result = new ArrayList<Group>();
        var assigned = new HashSet<UUID>();
        for (UUID id : cars.keySet()) {
            if (assigned.contains(id)) continue;
            Group group = group(id);
            group.cars().forEach(c -> assigned.add(c.definition.id()));
            result.add(group);
        }
        return result;
    }

    Group group(UUID id) {
        car(id);
        Set<UUID> ids = new HashSet<>();
        ids.add(id);
        boolean changed;
        do {
            changed = false;
            for (Connection c : connections) {
                UUID first = owner(c.first()), second = owner(c.second());
                if (ids.contains(first) || ids.contains(second)) {
                    changed |= ids.add(first); changed |= ids.add(second);
                }
            }
        } while (changed);
        return new Group(cars.values().stream().filter(c -> ids.contains(c.definition.id())).toList());
    }

    UUID owner(UUID endpoint) {
        return couplings.owner(endpoint);
    }
    CouplingEndpoint endpoint(UUID id) {
        return couplings.endpoint(id);
    }
    Car car(UUID id) {
        Car car = cars.get(id);
        if (car == null) throw new IllegalArgumentException("Unknown carriage " + id);
        return car;
    }
    static double momentumVelocity(Group a, Group b) {
        return VehicleRuntimeDynamics.momentumVelocity(a, b);
    }
    static void finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name);
    }

    record Group(List<Car> cars) {
        double mass() { return cars.stream().mapToDouble(c -> c.definition.massKg()).sum(); }
        double speed() { return cars.getFirst().speed; }
        void velocity(double velocity) { cars.forEach(c -> c.speed = velocity); }
        boolean contains(UUID id) { return cars.stream().anyMatch(c -> c.definition.id().equals(id)); }
    }
    static final class Car {
        CarriageDefinition definition;
        final Map<UUID, TrainCommand> controls = new LinkedHashMap<>();
        double distance, speed;
        Car(CarriageDefinition definition, double distance, double speed) {
            this.definition = definition; this.distance = distance; this.speed = speed;
            definition.bogeys().forEach(b -> controls.put(b.id(), TrainCommand.NEUTRAL));
        }
    }

    @Override
    public void addCarriage(VehicleBridge.CarriageDefinitionView definition, double distance, double speed) {
        addCarriage((CarriageDefinition) definition, distance, speed);
    }

}
