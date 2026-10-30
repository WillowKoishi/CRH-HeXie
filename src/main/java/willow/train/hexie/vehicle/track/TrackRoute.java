package willow.train.hexie.vehicle.track;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A finite, explicitly chosen directed route. Distances use the sampled geometric
 * arc length, not a backend's curve parameter or approximate declared length.
 * Build on the backend's owning thread and rebuild after a topology/geometry edit.
 * This class neither chooses a junction nor extends/wraps a route at its ends.
 */
public final class TrackRoute implements TrackPath {
    private static final double TOLERANCE = 1e-4;
    private static final int MAX_DEPTH = 20;
    private static final int MAX_INTERVALS = 65_536;
    private final List<Span> spans;
    private final double[] ends;

    public TrackRoute(List<? extends TrackGeometry> segments) {
        Objects.requireNonNull(segments);
        if (segments.isEmpty()) throw new IllegalArgumentException("A route needs a segment");
        List<Span> mapped = new ArrayList<>(segments.size());
        for (TrackGeometry geometry : segments) mapped.add(new Span(new ArcMap(geometry), false, false));
        spans = List.copyOf(mapped);
        ends = measure(spans);
    }

    private TrackRoute(List<Span> spans, boolean alreadyMapped) {
        this.spans = List.copyOf(spans);
        ends = measure(spans);
    }

    /** Geometry is always supplied in its canonical direction, independently of route travel. */
    public record DirectedSegment(TrackGeometry geometry, boolean reversed) {
        public DirectedSegment {
            Objects.requireNonNull(geometry);
        }
    }

    /** Measure canonical geometry, while exposing parameters in each selected edge's direction. */
    public static TrackRoute directed(List<DirectedSegment> segments) {
        Objects.requireNonNull(segments);
        if (segments.isEmpty()) throw new IllegalArgumentException("A route needs a segment");
        List<Span> mapped = new ArrayList<>(segments.size());
        for (DirectedSegment segment : segments)
            mapped.add(new Span(new ArcMap(segment.geometry), segment.reversed, segment.reversed));
        return new TrackRoute(mapped, true);
    }

    public double length() { return ends[ends.length - 1]; }
    @Override public double minimumDistance() { return 0; }
    @Override public double maximumDistance() { return length(); }

    /** Both endpoints are valid. At an internal junction, use the next segment's frame. */
    public TrackGeometry.Sample sample(double distance) {
        return locate(distance).sample();
    }

    /**
     * Segment index follows this route's order. Parameter follows the original geometry for ordinary
     * segments, or the selected edge direction for directed segments. Reversing a route preserves it.
     */
    public record Location(int segmentIndex, double parameter, TrackGeometry.Sample sample) {}

    public Location locate(double distance) {
        int index = segmentIndex(distance);
        double start = index == 0 ? 0 : ends[index - 1];
        Span span = spans.get(index);
        double parameter = span.parameter(distance - start);
        return new Location(index, parameter, span.atParameter(parameter));
    }

    /** Cached arc coordinates remain available while the backend replaces its graph objects. */
    public int segmentIndex(double distance) {
        if (!Double.isFinite(distance)) throw new IllegalArgumentException("Distance must be finite");
        if (distance < 0 || distance > length())
            throw new IndexOutOfBoundsException("Distance " + distance + " outside route [0, " + length() + "]");
        int index = Arrays.binarySearch(ends, distance);
        return index >= 0 ? Math.min(index + 1, spans.size() - 1) : -index - 1;
    }

    /** Inverse of locate, using exactly the same measured arc table and exposed segment parameter. */
    public double distanceAt(int segmentIndex, double parameter) {
        if (segmentIndex < 0 || segmentIndex >= spans.size()) throw new IndexOutOfBoundsException(segmentIndex);
        if (!Double.isFinite(parameter) || parameter < 0 || parameter > 1)
            throw new IllegalArgumentException("Parameter must be in [0, 1]");
        Span span = spans.get(segmentIndex);
        double local = span.map.distanceAt(span.canonicalParameter(parameter));
        return (segmentIndex == 0 ? 0 : ends[segmentIndex - 1])
                + (span.reverse ? span.map.length() - local : local);
    }

    /** Measure only new edges; existing curve distance maps remain unchanged. */
    public TrackRoute extended(List<? extends TrackGeometry> before, List<? extends TrackGeometry> after) {
        if (before.isEmpty() && after.isEmpty()) return this;
        List<Span> mapped = new ArrayList<>(before.size() + spans.size() + after.size());
        for (TrackGeometry geometry : before) mapped.add(new Span(new ArcMap(geometry), false, false));
        mapped.addAll(spans);
        for (TrackGeometry geometry : after) mapped.add(new Span(new ArcMap(geometry), false, false));
        return new TrackRoute(mapped, true);
    }

    /** Extend using canonical geometry, retaining every existing measured distance map and parameter. */
    public TrackRoute extendedDirected(List<DirectedSegment> before, List<DirectedSegment> after) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        if (before.isEmpty() && after.isEmpty()) return this;
        List<Span> mapped = new ArrayList<>(before.size() + spans.size() + after.size());
        for (DirectedSegment segment : before)
            mapped.add(new Span(new ArcMap(segment.geometry), segment.reversed, segment.reversed));
        mapped.addAll(spans);
        for (DirectedSegment segment : after)
            mapped.add(new Span(new ArcMap(segment.geometry), segment.reversed, segment.reversed));
        return new TrackRoute(mapped, true);
    }

    /** Retain a moving route window without remeasuring any of its curve geometry. */
    public TrackRoute slice(int firstSegment, int endSegment) {
        if (firstSegment < 0 || endSegment > spans.size() || firstSegment >= endSegment)
            throw new IndexOutOfBoundsException("Invalid route segment window");
        return firstSegment == 0 && endSegment == spans.size() ? this
                : new TrackRoute(spans.subList(firstSegment, endSegment), true);
    }

    /** Reuses the distance maps; reversing changes tangent and signed cant, not the reference up. */
    public TrackRoute reversed() {
        List<Span> reversed = new ArrayList<>(spans.size());
        for (int i = spans.size() - 1; i >= 0; i--) {
            Span span = spans.get(i);
            reversed.add(new Span(span.map, !span.reverse, span.parameterReversed));
        }
        return new TrackRoute(reversed, true);
    }

    private static double[] measure(List<Span> spans) {
        double[] ends = new double[spans.size()];
        double total = 0;
        for (int i = 0; i < spans.size(); i++) {
            if (i > 0 && distance(spans.get(i - 1).sample(spans.get(i - 1).map.length()).position(),
                    spans.get(i).sample(0).position()) > TOLERANCE)
                throw new IllegalArgumentException("Disconnected route at segment " + i);
            total += spans.get(i).map.length();
            if (!Double.isFinite(total)) throw new IllegalArgumentException("Route is too long");
            ends[i] = total;
        }
        return ends;
    }

    private record Span(ArcMap map, boolean reverse, boolean parameterReversed) {
        TrackGeometry.Sample sample(double distance) {
            return atParameter(parameter(distance));
        }

        double parameter(double distance) {
            // Only absorb arithmetic roundoff within this segment. Public route bounds stay strict.
            double local = Math.max(0, Math.min(map.length(), distance));
            double canonical = map.parameter(reverse ? map.length() - local : local);
            return parameterReversed ? 1 - canonical : canonical;
        }

        double canonicalParameter(double parameter) {
            return parameterReversed ? 1 - parameter : parameter;
        }

        TrackGeometry.Sample atParameter(double parameter) {
            TrackGeometry.Sample sample = map.geometry.sample(canonicalParameter(parameter));
            return reverse ? TrackRoute.reverse(sample) : sample;
        }
    }

    static TrackGeometry.Sample reverse(TrackGeometry.Sample sample) {
        return new TrackGeometry.Sample(sample.position(), sample.tangent().scale(-1), sample.normal(),
                -sample.cantRadians());
    }

    private static double distance(TrackGeometry.Vector a, TrackGeometry.Vector b) {
        TrackGeometry.Vector delta = a.subtract(b);
        return Math.sqrt(delta.dot(delta));
    }

    private static TrackGeometry.Vector lerp(TrackGeometry.Vector a, TrackGeometry.Vector b, double t) {
        return a.subtract(a.subtract(b).scale(t));
    }

    /**
     * Adaptive polyline with checked quarter/midpoint position and length errors.
     * Maximum parameter span is 1/64 even on straight segments, so nonlinear
     * parameterizations are measured too. The tolerance bounds the measured
     * subdivision error; arbitrary oscillations between samples are not supported.
     * Geometry exceeding the work/precision limits is rejected, never silently accepted.
     */
    private static final class ArcMap {
        private final TrackGeometry geometry;
        private final double[] parameters;
        private final double[] distances;

        ArcMap(TrackGeometry geometry) {
            this.geometry = Objects.requireNonNull(geometry);
            if (!Double.isFinite(geometry.length()) || geometry.length() <= 0)
                throw new IllegalArgumentException("Segment must have positive finite length");
            List<Knot> knots = new ArrayList<>();
            TrackGeometry.Vector first = geometry.sample(0).position();
            knots.add(new Knot(0, first));
            subdivide(0, first, 1, geometry.sample(1).position(), 0, knots);
            parameters = new double[knots.size()];
            distances = new double[knots.size()];
            for (int i = 0; i < knots.size(); i++) {
                parameters[i] = knots.get(i).parameter;
                if (i > 0) distances[i] = distances[i - 1] + distance(knots.get(i - 1).position, knots.get(i).position);
            }
            if (!Double.isFinite(length()) || length() <= 1e-9)
                throw new IllegalArgumentException("Segment geometry has no measurable length");
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

        private void subdivide(double from, TrackGeometry.Vector a, double to, TrackGeometry.Vector b,
                               int depth, List<Knot> knots) {
            double span = to - from;
            TrackGeometry.Vector q1 = geometry.sample(from + span * .25).position();
            TrackGeometry.Vector mid = geometry.sample(from + span * .5).position();
            TrackGeometry.Vector q3 = geometry.sample(from + span * .75).position();
            double polyline = distance(a, q1) + distance(q1, mid) + distance(mid, q3) + distance(q3, b);
            boolean accurate = span <= 1.0 / 64
                    && polyline - distance(a, b) <= TOLERANCE * span
                    && distance(q1, lerp(a, b, .25)) <= TOLERANCE
                    && distance(mid, lerp(a, b, .5)) <= TOLERANCE
                    && distance(q3, lerp(a, b, .75)) <= TOLERANCE;
            if (accurate) {
                if (knots.size() > MAX_INTERVALS) throw new IllegalArgumentException("Segment needs too many samples");
                knots.add(new Knot(to, b));
            } else {
                if (depth >= MAX_DEPTH) throw new IllegalArgumentException("Segment exceeds sampling precision limit");
                subdivide(from, a, from + span * .5, mid, depth + 1, knots);
                subdivide(from + span * .5, mid, to, b, depth + 1, knots);
            }
        }
        private record Knot(double parameter, TrackGeometry.Vector position) {}
    }
}
