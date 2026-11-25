package willow.train.hexie.railway.graph;

import java.util.List;
import java.util.Objects;
import willow.train.hexie.bridge.railway.RailwayGraphBridge;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.railway.engine.ArcLengthTable;
import willow.train.hexie.railway.engine.Direction;

/** Exposes a native edge's measured centreline and zero-cant frame to the existing track route solver. */
public record GraphTrackGeometry(ArcLengthTable table) implements TrackGeometry, RailwayGraphBridge.ArcTable {
    private static final Point3 FORWARD = new Point3(0, 0, 1);
    private static final Point3 UP = new Point3(0, 1, 0);

    @Override public double length() { return table.lengthM(); }
    @Override public int sampleCount() { return table.sampleCount(); }
    @Override public int linearSpanCount() { return sampleCount() - 1; }
    @Override public double linearSpanEnd(int index) {
        return table.stationDistance(Objects.checkIndex(index, linearSpanCount()) + 1) / length();
    }

    public static List<Point3> centreline(TrackGeometryView geometry) {
        return TrackArcLength.ArcMap.knots(geometry).stream().map(TrackArcLength.ArcMap.Knot::position)
                .map(p -> new Point3(p.x(), p.y(), p.z())).toList();
    }

    @Override
    public Sample sample(double parameter) {
        if (!(parameter >= 0 && parameter <= 1)) throw new IllegalArgumentException("Parameter outside [0, 1]");
        return sampleMetres(parameter * length(), false);
    }

    @Override
    public Sample sampleMetres(double metres, boolean reversed) {
        var pose = table.pose(metres, reversed ? Direction.BACKWARD : Direction.FORWARD);
        return new Sample(vector(pose.position()), vector(pose.rotation().rotate(FORWARD)),
                vector(pose.rotation().rotate(UP)), 0);
    }

    private static Vector vector(Point3 point) {
        return new Vector(point.x(), point.y(), point.z());
    }
}
