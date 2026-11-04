package willow.train.hexie.compat.create;

import java.util.LinkedHashMap;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import net.createmod.catnip.data.Pair;

/** Native reservation adapter. Shared route observations supply all driving/display constraints. */
final class CreateAutomaticRouteData {
    private CreateAutomaticRouteData() {}

    static boolean reserve(CompatibilityTrain train, double lookAhead, CreateWaypointSupport.Preview preview) {
        return observe(train, lookAhead, preview, true);
    }

    static boolean observe(CompatibilityTrain train, double lookAhead, CreateWaypointSupport.Preview preview, boolean reserve) {
        var nav = train.navigation;
        if (nav.destination == null || train.graph == null) return false;
        if (!Double.isFinite(nav.distanceToDestination)) return false;
        int direction = nav.destinationBehindTrain ? -1 : 1;
        boolean waypoint = CreateWaypointSupport.currentInstruction(train);
        var station = waypoint ? preview == null ? null : preview.station() : nav.destination;
        double destinationDistance = station == null ? 0 : waypoint ? preview.stationDistance() : nav.distanceToDestination;
        if (reserve) nav.destination.reserveFor(train);
        var leading = direction > 0 ? train.carriages.getFirst().getLeadingPoint()
                : train.carriages.getLast().getTrailingPoint();
        if (leading.edge == null) return false;
        var scout = CreateTrackCompatibility.copy(train.graph, leading);
        double scanDistance = station == null ? lookAhead : Math.min(Math.max(0, destinationDistance), lookAhead);
        var scan = new Scan();
        // Selectors consume a copy of the route. Only actual movement consumes the live switch itinerary.
        TravellingPoint.IEdgePointListener listener = (distance, event) -> {
            if (event.getFirst() == station) {
                reserve(train, scan, reserve);
                return true;
            }
            if (distance > scanDistance && scan.chainStart == null) return true;
            if (!(event.getFirst() instanceof SignalBoundary signal)) return false;
            var node = event.getSecond().getSecond();
            var observation = CreateSignalData.observe(signal, node, train);
            if (observation == null) return false;
            var groupId = observation.group();
            var group = Create.RAILWAYS.signalEdgeGroups.get(groupId);
            boolean primary = observation.primary(), chained = observation.chained();
            if (chained && scan.chainStart == null) {
                scan.chainStart = Pair.of(signal.id, primary);
                scan.chainDistance = distance;
            }
            // Missing data cannot become a native signal wait; keep only observed red/occupancy.
            boolean blocked = observation.occupancy() == willow.train.hexie.vehicle.control.CtcsControl.Occupancy.BLOCKED;
            if (blocked) {
                scan.blocked = scan.chainStart == null ? Pair.of(signal.id, primary) : scan.chainStart;
                scan.distance = scan.chainStart == null ? distance : scan.chainDistance;
                return true;
            }
            if (group != null) scan.chain.put(groupId, signal);
            if (!chained) {
                reserve(train, scan, reserve);
            }
            return false;
        };
        double scanned = 0;
        if (preview != null) preview.traverse(CreateWaypointSupport.DISPLAY_DISTANCE, listener);
        else scanned = scout.travel(train.graph,
                (waypoint ? Math.max(0, nav.distanceToDestination) + .001
                        : Math.max(scanDistance, Math.max(0, destinationDistance)) + 50) * direction,
                nav.controlSignalScout(), listener, scout.ignoreTurns());
        // Preserve native reservation/wait bookkeeping independently of the shared CTCS interpretation.
        if (scan.blocked == null) nav.ticksWaitingForSignal = 0;
        else nav.ticksWaitingForSignal++;
        nav.waitingForSignal = scan.blocked;
        nav.distanceToSignal = scan.blocked == null ? Double.MAX_VALUE : scan.distance;
        if (scan.blocked != null) return true;
        if (preview != null ? preview.blocked() && preview.observedDistance() < scanDistance
                : scout.blocked && Math.abs(scanned) < scanDistance) return false;
        return true;
    }

    private static void reserve(CompatibilityTrain train, Scan scan, boolean reserve) {
        if (reserve) scan.chain.forEach((id, boundary) -> {
            var group = Create.RAILWAYS.signalEdgeGroups.get(id);
            if (group != null) {
                group.reserved = boundary;
                train.reservedSignalBlocks.add(id);
            }
        });
        scan.chain.clear(); scan.chainStart = null;
    }

    private static final class Scan {
        final LinkedHashMap<UUID, SignalBoundary> chain = new LinkedHashMap<>();
        Pair<UUID, Boolean> chainStart, blocked;
        double chainDistance, distance;
    }
}
