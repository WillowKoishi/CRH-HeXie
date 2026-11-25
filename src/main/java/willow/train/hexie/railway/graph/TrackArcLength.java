package willow.train.hexie.railway.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import willow.train.hexie.bridge.railway.RailwayGraphBridge;
import willow.train.hexie.bridge.railway.TrackGeometryView;

// split from willow/train/hexie/vehicle/track/TrackRoute.java
final class TrackArcLength {
    private static final int MAX_DEPTH = 20;
    private static final int MAX_INTERVALS = 65_536;

    static double distance(TrackGeometryView.Vector a, TrackGeometryView.Vector b) {
        TrackGeometryView.Vector delta = a.subtract(b);
        return Math.sqrt(delta.dot(delta));
    }

    private static TrackGeometryView.Vector lerp(TrackGeometryView.Vector a, TrackGeometryView.Vector b, double t) {
        return a.subtract(a.subtract(b).scale(t));
    }

    /**
     * Adaptive polyline with checked quarter/midpoint position and length errors.
     * Maximum parameter span is 1/64 even on straight segments, so nonlinear
     * parameterizations are measured too. The tolerance bounds the measured
     * subdivision error; arbitrary oscillations between samples are not supported.
     * Geometry exceeding the work/precision limits is rejected, never silently accepted.
     */
    static final class ArcMap {
        final TrackGeometryView geometry;
        final double[] parameters;
        final double[] distances;

        ArcMap(TrackGeometryView geometry) {
            this.geometry = Objects.requireNonNull(geometry);
            if (!Double.isFinite(geometry.length()) || geometry.length() <= 0)
                throw new IllegalArgumentException("Segment must have positive finite length");
            if (geometry instanceof RailwayGraphBridge.ArcTable) {
                // The graph has already measured this polyline. Its parameter is exactly distance / length.
                if (geometry.length() <= 1e-9)
                    throw new IllegalArgumentException("Segment geometry has no measurable length");
                parameters = new double[]{0, 1};
                distances = new double[]{0, geometry.length()};
                return;
            }
            List<Knot> knots = knots(geometry);
            parameters = new double[knots.size()];
            distances = new double[knots.size()];
            for (int i = 0; i < knots.size(); i++) {
                parameters[i] = knots.get(i).parameter();
                if (i > 0) distances[i] = distances[i - 1] + distance(knots.get(i - 1).position(), knots.get(i).position());
            }
            if (!Double.isFinite(length()) || length() <= 1e-9)
                throw new IllegalArgumentException("Segment geometry has no measurable length");
        }

        static List<Knot> knots(TrackGeometryView geometry) {
            List<Knot> knots = new ArrayList<>();
            TrackGeometryView.Vector first = geometry.sample(0).position();
            knots.add(new Knot(0, first));
            int spans = geometry.linearSpanCount();
            if (spans == 0) {
                subdivide(geometry, 0, first, 1, geometry.sample(1).position(), 0, knots);
            } else {
                for (int i = 0; i < spans; i++) {
                    double end = geometry.linearSpanEnd(i);
                    if (!(end > knots.getLast().parameter() && end <= 1))
                        throw new IllegalArgumentException("Invalid linear span");
                    knots.add(new Knot(end, geometry.sample(end).position()));
                }
                if (knots.getLast().parameter() != 1) throw new IllegalArgumentException("Incomplete linear spans");
            }
            return knots;
        }

        double length() { return distances[distances.length - 1]; }

        double distanceAt(double parameter) {
            int at = Arrays.binarySearch(parameters, parameter);
            if (at >= 0) return distances[at];
            int next = -at - 1;
            double ratio = (parameter - parameters[next - 1]) / (parameters[next] - parameters[next - 1]);
            return distances[next - 1] + ratio * (distances[next] - distances[next - 1]);
        }

        double parameter(double distance) {
            int at = Arrays.binarySearch(distances, distance);
            if (at >= 0) return parameters[at];
            int next = -at - 1;
            double ratio = (distance - distances[next - 1]) / (distances[next] - distances[next - 1]);
            return parameters[next - 1] + ratio * (parameters[next] - parameters[next - 1]);
        }

        private static void subdivide(TrackGeometryView geometry, double from, TrackGeometryView.Vector a,
                                      double to, TrackGeometryView.Vector b, int depth, List<Knot> knots) {
            double span = to - from;
            TrackGeometryView.Vector q1 = geometry.sample(from + span * .25).position();
            TrackGeometryView.Vector mid = geometry.sample(from + span * .5).position();
            TrackGeometryView.Vector q3 = geometry.sample(from + span * .75).position();
            double polyline = distance(a, q1) + distance(q1, mid) + distance(mid, q3) + distance(q3, b);
            boolean accurate = span <= 1.0 / 64
                    && polyline - distance(a, b) <= TrackRoute.TOLERANCE * span
                    && distance(q1, lerp(a, b, .25)) <= TrackRoute.TOLERANCE
                    && distance(mid, lerp(a, b, .5)) <= TrackRoute.TOLERANCE
                    && distance(q3, lerp(a, b, .75)) <= TrackRoute.TOLERANCE;
            if (accurate) {
                if (knots.size() > MAX_INTERVALS) throw new IllegalArgumentException("Segment needs too many samples");
                knots.add(new Knot(to, b));
            } else {
                if (depth >= MAX_DEPTH) throw new IllegalArgumentException("Segment exceeds sampling precision limit");
                subdivide(geometry, from, a, from + span * .5, mid, depth + 1, knots);
                subdivide(geometry, from + span * .5, mid, to, b, depth + 1, knots);
            }
        }
        record Knot(double parameter, TrackGeometryView.Vector position) {}
    }
}
