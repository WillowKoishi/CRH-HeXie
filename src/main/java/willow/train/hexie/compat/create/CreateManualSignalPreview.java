package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Reads the timetable-free manual display route without binding an exit to the vehicle. */
final class CreateManualSignalPreview {
    private CreateManualSignalPreview() {}

    static void traverse(CreateRouteBinding route, TravellingPoint start, boolean forward, TravellingPoint.SteerDirection steer,
                         TravellingPoint.ITrackSelector itinerary, double range, TravellingPoint.IEdgePointListener listener) {
        var graph = route.graph();
        var point = CreateTrackCompatibility.copy(graph, start);
        if (!forward) point.reverse(graph);
        double offset = -route.previewDistanceOn(point.edge, point.position);
        var seen = new HashSet<TrackEdge>();
        while (offset < range && seen.add(point.edge)) {
            for (var event : point.edge.getEdgeData().getPoints()) {
                double position = event.getLocationOn(point.edge);
                if (position < point.position) continue;
                double distance = offset + route.previewDistanceOn(point.edge, position);
                if (distance > range || listener.test(distance, Pair.of(event, Couple.create(point.node1, point.node2)))) return;
            }
            offset += route.previewDistanceOn(point.edge, point.edge.getLength());
            if (offset >= range) return;
            var exits = CreateRouteBinding.continuations(graph, point.node1, point.node2, point.edge);
            if (exits.isEmpty()) return;
            var exit = exits.size() == 1 ? exits.getFirst() : choose(route, point, forward, steer, itinerary, exits, range - offset);
            if (!CreateRouteBinding.isOwnTrack(exit.getValue()) || exit.getValue().isInterDimensional()) return;
            point = CreateTrackCompatibility.point(graph, point.node2, exit.getKey(), exit.getValue(), 0, false);
        }
    }

    private static Map.Entry<TrackNode, TrackEdge> choose(CreateRouteBinding route, TravellingPoint point, boolean forward,
            TravellingPoint.SteerDirection steer, TravellingPoint.ITrackSelector itinerary,
            List<Map.Entry<TrackNode, TrackEdge>> exits, double range) {
        var remaining = exits.stream().filter(exit -> !onlyOpposing(route, point.node2, exit, range)).toList();
        // If every exit is prohibited, report the red on the existing choice.
        if (remaining.isEmpty()) remaining = exits;
        if (itinerary != null) return itinerary.apply(route.graph(), Pair.of(forward, remaining));
        var bound = route.boundExitAfter(point.edge);
        for (var exit : remaining) if (exit.getKey() == bound) return exit;
        if (remaining.size() == 1) return remaining.getFirst();
        return CreateRouteBinding.manualExit(route.graph(), point, forward, steer, remaining);
    }

    /** Inspect just this arm through ordinary nodes. Signals past its next switch belong to other candidates. */
    private static boolean onlyOpposing(CreateRouteBinding route, TrackNode from, Map.Entry<TrackNode, TrackEdge> exit, double range) {
        boolean opposing = false;
        var seen = new HashSet<TrackEdge>();
        while (range > 0 && seen.add(exit.getValue())) {
            var edge = exit.getValue();
            var to = exit.getKey();
            if (!CreateRouteBinding.isOwnTrack(edge) || edge.isInterDimensional()) break;
            for (var event : edge.getEdgeData().getPoints()) {
                if (!(event instanceof SignalBoundary signal)) continue;
                if (route.previewDistanceOn(edge, signal.getLocationOn(edge)) > range) break;
                boolean side = signal.isPrimary(to);
                if (!signal.blockEntities.get(side).isEmpty()) return false;
                if (!signal.blockEntities.get(!side).isEmpty()) opposing = true;
            }
            range -= route.previewDistanceOn(edge, edge.getLength());
            var next = CreateRouteBinding.continuations(route.graph(), from, to, edge);
            if (next.size() != 1) break;
            from = to;
            exit = next.getFirst();
        }
        return opposing;
    }
}
