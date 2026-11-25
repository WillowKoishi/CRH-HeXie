package willow.train.hexie.bridge.railway;

import java.util.List;

/** Creates and locates routes using railway-owned track geometry. */
public interface RailwayBridge {
    default RailwayGraphBridge railwayGraph() {
        throw new UnsupportedOperationException("This railway backend does not provide a region graph");
    }

    TrackRouteDirectedSegmentView createTrackRouteDirectedSegment(TrackGeometryView geometry, boolean reversed);

    TrackRouteOperations trackRoute();

    interface TrackRouteOperations {
        TrackRouteView create(List<? extends TrackGeometryView> segments);
        TrackRouteView directed(List<TrackRouteDirectedSegmentView> segments);
    }

    interface TrackRouteDirectedSegmentView {
        TrackGeometryView geometry();
        boolean reversed();
    }

    interface TrackRouteLocationView {
        int segmentIndex();
        double parameter();
        TrackGeometryView.Sample sample();
    }
}
