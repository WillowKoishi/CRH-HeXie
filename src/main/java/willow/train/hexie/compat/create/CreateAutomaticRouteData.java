package willow.train.hexie.compat.create;

import java.util.LinkedHashMap;
import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import net.createmod.catnip.data.Pair;
import willow.train.hexie.vehicle.control.AutomaticTrainControl;

/** Track adapter: route distance, signal occupancy and reservations; no acceleration or speed commands. */
final class CreateAutomaticRouteData {
    private CreateAutomaticRouteData() {}

    static AutomaticTrainControl.Target read(CompatibilityTrain train, double serviceDeceleration) {
        var nav = train.navigation;
        if (nav.destination == null || train.graph == null) return null;
        double destinationDistance = nav.distanceToDestination;
        if (!Double.isFinite(destinationDistance)) return null;
        int direction = nav.destinationBehindTrain ? -1 : 1;
        nav.destination.reserveFor(train);
        var leading = direction > 0 ? train.carriages.getFirst().getLeadingPoint()
                : train.carriages.getLast().getTrailingPoint();
        if (leading.edge == null) return null;
        var scout = new TravellingPoint(leading.node1, leading.node2, leading.edge, leading.position, false);
        double speed = Math.abs(train.speed) * 20;
        double brakingDistance = serviceDeceleration > 0 ? speed * speed / (2 * serviceDeceleration * .85) : 0;
        double scanDistance = Math.min(Math.max(0, destinationDistance), Math.max(32, brakingDistance + speed * .5 + 8));
        var scan = new Scan();
        // Selectors consume a copy of the route. Only actual movement consumes the live switch itinerary.
        scout.travel(train.graph, (Math.max(0, destinationDistance) + 50) * direction, nav.controlSignalScout(), (distance, event) -> {
            if (event.getFirst() == nav.destination) {
                reserve(train, scan);
                return true;
            }
            if (distance > scanDistance && scan.chainStart == null) return true;
            if (!(event.getFirst() instanceof SignalBoundary signal)) return false;
            var node = event.getSecond().getSecond();
            var groupId = signal.getGroup(node);
            var group = Create.RAILWAYS.signalEdgeGroups.get(groupId);
            boolean primary = java.util.Objects.equals(groupId, signal.groups.getFirst());
            boolean chained = signal.types.get(primary) == SignalType.CROSS_SIGNAL;
            if (chained && scan.chainStart == null) {
                scan.chainStart = Pair.of(signal.id, primary);
                scan.chainDistance = distance;
            }
            // Unknown groups are not permission to depart. The next read retries after graph updates.
            boolean blocked = group == null || signal.isForcedRed(node) || group.isOccupiedUnless(train);
            if (blocked) {
                scan.blocked = scan.chainStart == null ? Pair.of(signal.id, primary) : scan.chainStart;
                scan.distance = scan.chainStart == null ? distance : scan.chainDistance;
                return true;
            }
            scan.chain.put(groupId, signal);
            if (!chained) {
                reserve(train, scan);
            }
            return false;
        }, scout.ignoreTurns());
        // Do not clear an unresolved chain at the edge of our look-ahead window.
        if (scan.blocked == null && scan.chainStart != null) {
            scan.blocked = scan.chainStart; scan.distance = scan.chainDistance;
        }
        if (scan.blocked == null) nav.ticksWaitingForSignal = 0;
        else nav.ticksWaitingForSignal++;
        nav.waitingForSignal = scan.blocked;
        nav.distanceToSignal = scan.blocked == null ? Double.MAX_VALUE : scan.distance;
        if (scan.blocked != null)
            return new AutomaticTrainControl.Target(direction, scan.distance, Double.POSITIVE_INFINITY, false);
        if (scout.blocked) return null;
        return new AutomaticTrainControl.Target(direction, destinationDistance, Double.POSITIVE_INFINITY, true);
    }

    private static void reserve(CompatibilityTrain train, Scan scan) {
        scan.chain.forEach((id, boundary) -> {
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
