package willow.train.hexie.compat.create.signal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.CreateTrainBridge;

// split from CreateTrainBridge.java
public final class CreateTrainRouteUpdates {
    private final CreateTrainBridge trainBridge;

    public CreateTrainRouteUpdates(CreateTrainBridge trainBridge) { this.trainBridge = trainBridge; }

    public final Map<UUID, ExtensionAttempt> extensionAttempts = new HashMap<>();

    public void refreshActiveRoute() {
        var refreshed = refreshedRoute(trainBridge.motion.runtime());
        if (refreshed.binding() == trainBridge.motion.route()) return;
        if (!trainBridge.motion.rebindRoute(refreshed.binding(), refreshed.distanceOffset()))
            throw new CreateRouteBinding.RouteInvalidException();
        extensionAttempts.clear();
        for (var train : trainBridge.trains.values()) {
            if (train.graph == null) continue;
            train.graph = trainBridge.motion.route().graph();
            trainBridge.sync(train);
        }
    }

    /** Prepare a current binding for selected live consists before changing their ownership. */
    public CreateRouteBinding.Extension refreshedRoute(VehicleRuntimeView selected) {
        double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
        for (var point : trainBridge.trackPoints.pointLookup.values()) {
            var support = selected.placement().bogeys().get(point.bogey);
            if (support == null) continue;
            double distance = support.distance() + point.offset;
            minimum = Math.min(minimum, distance);
            maximum = Math.max(maximum, distance);
        }
        double lookAhead = 2;
        for (var carriage : selected.definitions()) {
            lookAhead = Math.max(lookAhead, Math.abs(selected.speed(carriage.id())) / 20 + 2);
            for (var bogey : carriage.bogeys()) {
                double distance = selected.placement().bogeys().get(bogey.id()).distance();
                minimum = Math.min(minimum, distance);
                maximum = Math.max(maximum, distance);
            }
        }
        return trainBridge.motion.route().refresh(minimum - lookAhead, maximum + lookAhead);
    }

    public void extendNearBoundary(CompatibilityTrain train) {
        var member = train.carriageIds.getFirst();
        double speed = trainBridge.motion.runtime().speed(member);
        boolean simpleManual = trainBridge.motion.isSimpleControlSelected(member) && !trainBridge.motion.isApproachingStation(member);
        var destination = simpleManual || trainBridge.motion.cabSelection(member).conflicting() ? null : train.navigation.destination;
        double direction = speed != 0 ? speed : destination != null
                ? (train.navigation.destinationBehindTrain ? 1 : -1)
                : trainBridge.motion.runtime().tractionDirection(train.carriageIds.getFirst());
        // At a stopped route end, integration cannot produce velocity until the selected
        // route is extended. Use the requested drive direction, not an inactive navigation flag.
        if (direction == 0) return;
        boolean increasing = direction > 0;
        var end = increasing ? train.carriages.getLast().getTrailingPoint() : train.carriages.getFirst().getLeadingPoint();
        double distance = trainBridge.binding(end).distance;
        double lookAhead = Math.max(2, Math.abs(speed) / 20 + 2);
        double minimumOccupied = Double.POSITIVE_INFINITY, maximumOccupied = Double.NEGATIVE_INFINITY;
        for (var point : trainBridge.trackPoints.pointLookup.values()) {
            double occupied = trainBridge.currentDistance(point);
            minimumOccupied = Math.min(minimumOccupied, occupied);
            maximumOccupied = Math.max(maximumOccupied, occupied);
        }
        for (var bogey : trainBridge.motion.runtime().placement().bogeys().values()) {
            minimumOccupied = Math.min(minimumOccupied, bogey.distance());
            maximumOccupied = Math.max(maximumOccupied, bogey.distance());
        }
        var scout = destination == null ? null : train.navigation.controlSignalScout();
        CreateRouteBinding.RouteChoice choice = (graph, preview, extendingForward, choices) -> scout != null
                ? scout.apply(graph, net.createmod.catnip.data.Pair.of(!extendingForward, choices))
                : CreateRouteBinding.manualExit(graph, preview, !extendingForward, train.manualSteer, choices);
        if (trainBridge.motion.reselectRoute(increasing, choice, distance, lookAhead, minimumOccupied, maximumOccupied)) {
            trainBridge.finishRelocation();
            extensionAttempts.clear();
            return;
        }
        double remaining = increasing ? trainBridge.motion.route().path().maximumDistance() - distance
                : distance - trainBridge.motion.route().path().minimumDistance();
        if (remaining > lookAhead) return;
        var node = increasing ? end.node1 : end.node2;
        int adjacent = trainBridge.motion.route().graph().getConnectionsFrom(node).hashCode();
        var retainedEdge = trainBridge.motion.route().boundedPointAt(increasing ? minimumOccupied : maximumOccupied).edge;
        var attempt = new ExtensionAttempt(trainBridge.motion.route(), increasing, adjacent, train.manualSteer,
                destination, train.navigation.distanceStartedAt, retainedEdge);
        if (attempt.equals(extensionAttempts.get(train.id))) return;
        extensionAttempts.put(train.id, attempt);
        boolean extended = trainBridge.motion.extendRoute(increasing, choice, minimumOccupied, maximumOccupied);
        if (extended) {
            for (var compatible : trainBridge.trains.values()) {
                if (compatible.graph == null) continue;
                compatible.graph = trainBridge.motion.route().graph();
                trainBridge.sync(compatible);
            }
            extensionAttempts.clear();
        }
    }

    private record ExtensionAttempt(CreateRouteBinding route, boolean forward, int adjacent,
                                    Object steer, Object destination, double distanceStarted, Object retainedEdge) { }
}
