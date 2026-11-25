package willow.train.hexie.railway.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.DoubleUnaryOperator;

/** Route coordinates increase along the ordered legs, even when a leg traverses an edge backwards. */
public final class SignalPathIndex {
    public record Leg(DirectedEdge edge, double lengthM) {}
    public record Delta(Set<SignalAnchor> subscribe, Set<SignalAnchor> unsubscribe) {
        public Delta {
            subscribe = Set.copyOf(subscribe);
            unsubscribe = Set.copyOf(unsubscribe);
        }
    }

    private final NavigableMap<Double, List<SignalAnchor>> stations = new TreeMap<>();
    private final double length;
    private final boolean closed;

    public SignalPathIndex(List<Leg> route, Collection<SignalAnchor> anchors, boolean closed) {
        var byEdge = new HashMap<Long, List<SignalAnchor>>();
        for (var anchor : anchors) byEdge.computeIfAbsent(anchor.edgeId(), id -> new ArrayList<>()).add(anchor);
        double offset = 0;
        for (var leg : route) {
            if (!(leg.lengthM() > 0) || !Double.isFinite(leg.lengthM())) throw new IllegalArgumentException("Invalid leg length");
            for (var anchor : byEdge.getOrDefault(leg.edge().edgeId(), List.of())) {
                double s = anchor.sM();
                if (!(s >= 0 && s <= leg.lengthM())) throw new IllegalArgumentException("Anchor outside edge");
                if (leg.edge().direction() == Direction.BACKWARD) s = leg.lengthM() - s;
                stations.computeIfAbsent(offset + s, k -> new ArrayList<>()).add(anchor);
            }
            offset += leg.lengthM();
        }
        length = offset;
        this.closed = closed;
        if (closed && length == 0) throw new IllegalArgumentException("Empty circuit");
        if (closed && stations.containsKey(length)) {
            stations.computeIfAbsent(0d, k -> new ArrayList<>()).addAll(stations.remove(length));
        }
    }

    /** One window per train. The braking model takes speed in m/s and returns distance in metres. */
    public final class Window {
        private Set<SignalAnchor> active = Set.of();

        public Delta slide(double headM, Direction travel, double sightM, double speedMps,
                           DoubleUnaryOperator brakingDistance, double marginM) {
            double brake = brakingDistance.applyAsDouble(Math.abs(speedMps));
            if (!(sightM >= 0 && marginM >= 0 && brake >= 0)) throw new IllegalArgumentException("Negative or NaN window");
            double ahead = Math.max(sightM, brake + marginM);
            double low = headM - (travel == Direction.FORWARD ? sightM : ahead);
            double high = headM + (travel == Direction.FORWARD ? ahead : sightM);
            var next = new HashSet<SignalAnchor>();
            if (!closed) {
                collect(low, high, next);
            } else if (high - low >= length) {
                collect(0, length, next);
            } else {
                double begin = low % length;
                if (begin < 0) begin += length;
                double end = begin + (high - low);
                collect(begin, Math.min(length, end), next);
                if (end >= length) collect(0, end - length, next);
            }
            var added = new HashSet<>(next);
            added.removeAll(active);
            var removed = new HashSet<>(active);
            removed.removeAll(next);
            active = next;
            return new Delta(added, removed);
        }

        public boolean subscribed(SignalAnchor anchor) { return active.contains(anchor); }
    }

    private void collect(double low, double high, Set<SignalAnchor> result) {
        stations.subMap(low, true, high, true).values().forEach(result::addAll);
    }
}
