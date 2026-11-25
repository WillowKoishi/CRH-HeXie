package willow.train.hexie.railway.graph;

// split from willow/train/hexie/vehicle/track/TrackRoute.java

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import willow.train.hexie.bridge.railway.RailwayBridge;
import willow.train.hexie.bridge.railway.RailwayGraphBridge;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.bridge.railway.TrackRouteView;
import willow.train.hexie.railway.graph.TrackArcLength.ArcMap;

/**
 * A finite, explicitly chosen directed route. Distances use the sampled geometric
 * arc length, not a backend's curve parameter or approximate declared length.
 * Build on the backend's owning thread and rebuild after a topology/geometry edit.
 * This class neither chooses a junction nor extends/wraps a route at its ends.
 */
public final class TrackRoute implements TrackPath, TrackRouteView {
    static final double TOLERANCE = 1e-4;

    private final List<Span> spans;
    private final double[] ends;

    public TrackRoute(List<? extends TrackGeometryView> segments) {
        Objects.requireNonNull(segments);
        if (segments.isEmpty()) throw new IllegalArgumentException("A route needs a segment");
        List<Span> mapped = new ArrayList<>(segments.size());
        for (TrackGeometryView geometry : segments) mapped.add(new Span(new ArcMap(geometry), false, false));
        spans = List.copyOf(mapped);
        ends = measure(spans);
    }

    private TrackRoute(List<Span> spans, boolean alreadyMapped) {
        this.spans = List.copyOf(spans);
        ends = measure(spans);
    }

    /** Geometry is always supplied in its canonical direction, independently of route travel. */
    public record DirectedSegment(TrackGeometryView geometry, boolean reversed) implements RailwayBridge.TrackRouteDirectedSegmentView {
        public DirectedSegment {
            Objects.requireNonNull(geometry);
        }
    }

    /** Measure canonical geometry, while exposing parameters in each selected edge's direction. */
    public static TrackRoute directed(List<RailwayBridge.TrackRouteDirectedSegmentView> segments) {
        Objects.requireNonNull(segments);
        if (segments.isEmpty()) throw new IllegalArgumentException("A route needs a segment");
        List<Span> mapped = new ArrayList<>(segments.size());
        for (var segment : segments)
            mapped.add(new Span(new ArcMap(segment.geometry()), segment.reversed(), segment.reversed()));
        return new TrackRoute(mapped, true);
    }

    public double length() { return ends[ends.length - 1]; }
    @Override public double minimumDistance() { return 0; }
    @Override public double maximumDistance() { return length(); }

    /** Both endpoints are valid. At an internal junction, use the next segment's frame. */
    public TrackGeometryView.Sample sample(double distance) {
        return locate(distance).sample();
    }

    @Override public boolean allowsBogeyTravel(String modelId, double from, double to) {
        int first = segmentIndex(Math.min(from, to));
        int last = segmentIndex(Math.max(from, to));
        for (int i = first; i <= last; i++)
            if (!spans.get(i).map().geometry.allowsBogey(modelId)) return false;
        return true;
    }

    /**
     * Segment index follows this route's order. Parameter follows the original geometry for ordinary
     * segments, or the selected edge direction for directed segments. Reversing a route preserves it.
     */
    public record Location(int segmentIndex, double parameter, TrackGeometryView.Sample sample) implements RailwayBridge.TrackRouteLocationView {}

    public Location locate(double distance) {
        int index = segmentIndex(distance);
        double start = index == 0 ? 0 : ends[index - 1];
        Span span = spans.get(index);
        double local = distance - start;
        double parameter = span.parameter(local);
        return new Location(index, parameter, span.sample(local, parameter));
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
        double local = span.map().distanceAt(span.canonicalParameter(parameter));
        return (segmentIndex == 0 ? 0 : ends[segmentIndex - 1])
                + (span.reverse() ? span.map().length() - local : local);
    }

    /** Measure only new edges; existing curve distance maps remain unchanged. */
    public TrackRoute extended(List<? extends TrackGeometryView> before, List<? extends TrackGeometryView> after) {
        if (before.isEmpty() && after.isEmpty()) return this;
        List<Span> mapped = new ArrayList<>(before.size() + spans.size() + after.size());
        for (TrackGeometryView geometry : before) mapped.add(new Span(new ArcMap(geometry), false, false));
        mapped.addAll(spans);
        for (TrackGeometryView geometry : after) mapped.add(new Span(new ArcMap(geometry), false, false));
        return new TrackRoute(mapped, true);
    }

    /** Extend using canonical geometry, retaining every existing measured distance map and parameter. */
    public TrackRoute extendedDirected(List<RailwayBridge.TrackRouteDirectedSegmentView> before, List<RailwayBridge.TrackRouteDirectedSegmentView> after) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        if (before.isEmpty() && after.isEmpty()) return this;
        List<Span> mapped = new ArrayList<>(before.size() + spans.size() + after.size());
        for (var segment : before)
            mapped.add(new Span(new ArcMap(segment.geometry()), segment.reversed(), segment.reversed()));
        mapped.addAll(spans);
        for (var segment : after)
            mapped.add(new Span(new ArcMap(segment.geometry()), segment.reversed(), segment.reversed()));
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
            reversed.add(new Span(span.map(), !span.reverse(), span.parameterReversed()));
        }
        return new TrackRoute(reversed, true);
    }

    private static double[] measure(List<Span> spans) {
        double[] ends = new double[spans.size()];
        double total = 0;
        for (int i = 0; i < spans.size(); i++) {
            if (i > 0 && distance(spans.get(i - 1).sample(spans.get(i - 1).map().length()).position(),
                    spans.get(i).sample(0).position()) > TOLERANCE)
                throw new IllegalArgumentException("Disconnected route at segment " + i);
            total += spans.get(i).map().length();
            if (!Double.isFinite(total)) throw new IllegalArgumentException("Route is too long");
            ends[i] = total;
        }
        return ends;
    }

    record Span(ArcMap map, boolean reverse, boolean parameterReversed) {
        TrackGeometryView.Sample sample(double distance) {
            return sample(distance, parameter(distance));
        }

        double canonicalDistance(double distance) {
            // Only absorb arithmetic roundoff within this segment. Public route bounds stay strict.
            double local = Math.max(0, Math.min(map.length(), distance));
            return reverse ? map.length() - local : local;
        }

        double parameter(double distance) {
            double canonical = map.parameter(canonicalDistance(distance));
            return parameterReversed ? 1 - canonical : canonical;
        }

        double canonicalParameter(double parameter) {
            return parameterReversed ? 1 - parameter : parameter;
        }

        TrackGeometryView.Sample sample(double distance, double parameter) {
            if (map.geometry instanceof RailwayGraphBridge.ArcTable table)
                return table.sampleMetres(canonicalDistance(distance), reverse);
            TrackGeometryView.Sample sample = map.geometry.sample(canonicalParameter(parameter));
            return reverse ? TrackRoute.reverse(sample) : sample;
        }
    }

    static TrackGeometryView.Sample reverse(TrackGeometryView.Sample sample) {
        return new TrackGeometryView.Sample(sample.position(), sample.tangent().scale(-1), sample.normal(),
                -sample.cantRadians());
    }

    static double distance(TrackGeometryView.Vector a, TrackGeometryView.Vector b) {
        return TrackArcLength.distance(a, b);
    }

}
