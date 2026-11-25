package willow.train.hexie.railway.bridge;

import java.util.List;
import willow.train.hexie.bridge.railway.RailwayBridge;
import willow.train.hexie.bridge.railway.RailwayGraphBridge;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.railway.TrackRouteView;
import willow.train.hexie.railway.graph.TrackRoute;

final class RailwayPort implements RailwayBridge {
    private final RailwayGraphBridge graph;

    RailwayPort(RailwayGraphBridge graph) {
        this.graph = graph;
    }

    @Override
    public RailwayGraphBridge railwayGraph() {
        return graph;
    }

    private final TrackRouteOperations trackRoute = new TrackRouteOperations() {
        @Override
        public TrackRouteView create(List<? extends TrackGeometryView> segments) {
            return new TrackRoute(segments);
        }

        @Override
        public TrackRouteView directed(List<TrackRouteDirectedSegmentView> segments) {
            return TrackRoute.directed(segments);
        }
    };

    @Override
    public TrackRouteDirectedSegmentView createTrackRouteDirectedSegment(TrackGeometryView geometry,
            boolean reversed) {
        return new TrackRoute.DirectedSegment(geometry, reversed);
    }

    @Override
    public TrackRouteOperations trackRoute() {
        return trackRoute;
    }
}
