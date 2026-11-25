package willow.train.hexie.bridge.railway;

import java.util.List;

public interface TrackRouteView extends TrackPathView {
    double length();
    double minimumDistance();
    double maximumDistance();
    RailwayBridge.TrackRouteLocationView locate(double distance);
    int segmentIndex(double distance);
    double distanceAt(int segmentIndex, double parameter);
    TrackRouteView extendedDirected(List<RailwayBridge.TrackRouteDirectedSegmentView> before,
            List<RailwayBridge.TrackRouteDirectedSegmentView> after);
    TrackRouteView slice(int firstSegment, int endSegment);
}
