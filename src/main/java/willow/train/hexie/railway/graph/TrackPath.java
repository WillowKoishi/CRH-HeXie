package willow.train.hexie.railway.graph;

import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.railway.TrackPathView;

/** Read-only route coordinates. A backend owns route identity, topology and lifetime. */
public interface TrackPath extends TrackPathView {
    TrackGeometryView.Sample sample(double distance);
    default double minimumDistance() { return Double.NEGATIVE_INFINITY; }
    default double maximumDistance() { return Double.POSITIVE_INFINITY; }

    /** Check the entire travelled interval, including its endpoints, after a placement has been solved. */
    default boolean allowsBogeyTravel(String modelId, double from, double to) { return true; }

    /** An unbounded straight reference path, also useful for isolated runtime acceptance. */
    static TrackPath straight() {
        return distance -> new TrackGeometryView.Sample(new TrackGeometryView.Vector(0, 0, distance),
                new TrackGeometryView.Vector(0, 0, 1), new TrackGeometryView.Vector(0, 1, 0), 0);
    }
}
