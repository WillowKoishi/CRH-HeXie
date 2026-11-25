package willow.train.hexie.railway.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import willow.train.hexie.core.api.vehicle.Point3;

/** Keeps every polyline corner as well as the quarter-metre stations. Local +Z is forward. */
public final class ArcLengthTable {
    private final double[] distances;
    private final List<Point3> points;

    private ArcLengthTable(EdgeGeometry edge) {
        var offsets = new ArrayList<Double>();
        points = new ArrayList<>();
        Point3 start = edge.samples().getFirst();
        points.add(start);
        offsets.add(0d);
        double total = 0;
        double station = 0.25;
        for (int i = 1; i < edge.samples().size(); i++) {
            Point3 end = edge.samples().get(i);
            double length = Math.hypot(Math.hypot(end.x() - start.x(), end.y() - start.y()), end.z() - start.z());
            if (!Double.isFinite(length)) throw new IllegalArgumentException("Non-finite track");
            if (length == 0) continue;
            double next = total + length;
            while (station < next) {
                points.add(interpolate(start, end, (station - total) / length));
                offsets.add(station);
                station += 0.25;
            }
            if (station == next) station += 0.25;
            points.add(end);
            offsets.add(next);
            total = next;
            start = end;
        }
        if (total == 0) throw new IllegalArgumentException("Zero-length track");
        distances = offsets.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public double lengthM() { return distances[distances.length - 1]; }
    public int sampleCount() { return distances.length; }
    public double stationDistance(int index) { return distances[index]; }

    private static Point3 interpolate(Point3 a, Point3 b, double fraction) {
        return new Point3(a.x() + (b.x() - a.x()) * fraction,
                          a.y() + (b.y() - a.y()) * fraction,
                          a.z() + (b.z() - a.z()) * fraction);
    }

    public Pose pose(double sM, Direction direction) {
        if (!(sM >= 0 && sM <= lengthM())) throw new IllegalArgumentException("Outside edge");
        int at = sM == 0 ? 0 : Arrays.binarySearch(distances, sM);
        int segment = at < 0 ? -at - 2 : at;
        if (at > 0 && direction == Direction.BACKWARD) segment--;
        segment = Math.min(segment, points.size() - 2);
        Point3 a = points.get(segment), b = points.get(segment + 1);
        double fraction = (sM - distances[segment]) / (distances[segment + 1] - distances[segment]);
        Point3 position = at >= 0 ? points.get(at) : interpolate(a, b, fraction);
        double sign = direction == Direction.FORWARD ? 1 : -1;
        double dx = (b.x() - a.x()) * sign, dy = (b.y() - a.y()) * sign, dz = (b.z() - a.z()) * sign;
        double yaw = Math.atan2(dx, dz) / 2;
        double pitch = -Math.atan2(dy, Math.hypot(dx, dz)) / 2;
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        return new Pose(position, new Quat(cy * sp, sy * cp, -sy * sp, cy * cp));
    }

    public static final class Cache {
        private record Key(long edgeId, long revision) {}
        private final int capacity;
        private final LinkedHashMap<Key, ArcLengthTable> tables = new LinkedHashMap<>(16, 0.75f, true);

        public Cache(int capacity) {
            if (capacity < 1) throw new IllegalArgumentException("Empty LRU");
            this.capacity = capacity;
        }

        public ArcLengthTable get(EdgeGeometry edge) {
            var key = new Key(edge.edgeId(), edge.geometryRevision());
            var table = tables.computeIfAbsent(key, ignored -> new ArcLengthTable(edge));
            if (tables.size() > capacity) tables.pollFirstEntry();
            return table;
        }
    }
}
