package willow.train.hexie.vehicle.track;

/** Read-only route coordinates. A backend owns route identity, topology and lifetime. */
public interface TrackPath {
    TrackGeometry.Sample sample(double distance);
    default double minimumDistance() { return Double.NEGATIVE_INFINITY; }
    default double maximumDistance() { return Double.POSITIVE_INFINITY; }

    /** An unbounded straight reference path, also useful for isolated runtime acceptance. */
    static TrackPath straight() {
        return distance -> new TrackGeometry.Sample(new TrackGeometry.Vector(0, 0, distance),
                new TrackGeometry.Vector(0, 0, 1), new TrackGeometry.Vector(0, 1, 0), 0);
    }
}
