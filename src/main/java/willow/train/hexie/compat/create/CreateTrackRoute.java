package willow.train.hexie.compat.create;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import willow.train.hexie.vehicle.track.TrackGeometry;
import willow.train.hexie.vehicle.track.TrackRoute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only adapter for a route explicitly supplied by train control; never chooses junction exits. */
public final class CreateTrackRoute {
    private CreateTrackRoute() {}

    /** Call on the graph's owning thread. The supplied nodes must be this graph's node instances. */
    public static TrackRoute build(TrackGraph graph, List<TrackNode> directedNodes) {
        Objects.requireNonNull(graph);
        List<TrackNode> nodes = List.copyOf(directedNodes);
        if (nodes.size() < 2) throw new IllegalArgumentException("A route requires at least two nodes");
        List<TrackGeometry> segments = new ArrayList<>(nodes.size() - 1);
        for (TrackNode node : nodes)
            if (graph.locateNode(node.getLocation()) != node)
                throw new IllegalArgumentException("Route node is not owned by this graph");
        for (int i = 1; i < nodes.size(); i++) {
            Map<TrackNode, TrackEdge> outgoing = graph.getConnectionsFrom(nodes.get(i - 1));
            TrackEdge edge = outgoing == null ? null : outgoing.get(nodes.get(i));
            if (edge == null) throw new IllegalArgumentException("No directed track connection at route step " + i);
            // Constructor rejects Create/foreign materials and interdimensional edges.
            segments.add(new CreateTrackGeometry(graph, edge));
        }
        return new TrackRoute(segments);
    }
}
