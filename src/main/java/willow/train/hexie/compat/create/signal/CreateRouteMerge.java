package willow.train.hexie.compat.create.signal;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.railway.RailwayBridge.TrackRouteDirectedSegmentView;
import willow.train.hexie.bridge.railway.TrackRouteView;
import willow.train.hexie.compat.create.track.CreateRouteGeometry;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;

// split from CreateRouteBinding.java
public final class CreateRouteMerge {
    private final CreateRouteBinding routeBinding;

    public CreateRouteMerge(CreateRouteBinding routeBinding) { this.routeBinding = routeBinding; }

    /** First cursors become s + firstOffset; second cursors become (+/-s) + secondOffset. */
    public record Merge(CreateRouteBinding route, double firstOffset, double secondOffset, boolean reverseSecond) {}

    record MergePlan(List<TrackNode> nodes, int firstStart, int secondOrigin, boolean reverseSecond) {}

    /** Build the coupling corridor from occupied track, not obsolete choices outside the trains. */
    public Merge connectForCoupling(CreateRouteBinding other, double firstMin, double firstMax,
            double secondMin, double secondMax) {
        CreateRouteBinding.Extension a = occupied(firstMin, firstMax), b = other.occupied(secondMin, secondMax);
        Merge joined = a.binding().merge(b.binding());
        return new Merge(joined.route, joined.firstOffset + a.distanceOffset(),
                joined.secondOffset + (joined.reverseSecond ? -b.distanceOffset() : b.distanceOffset()), joined.reverseSecond);
    }

    public CreateRouteBinding.Extension occupied(double min, double max) {
        int first = routeBinding.route.segmentIndex(routeBinding.boundDistance(min));
        int last = routeBinding.route.segmentIndex(routeBinding.boundDistance(max)) + 1;
        double offset = -routeBinding.route.distanceAt(first, 0);
        return new CreateRouteBinding.Extension(new CreateRouteBinding(routeBinding, routeBinding.nodes.subList(first, last + 1),
                routeBinding.segments.subList(first, last), routeBinding.route.slice(first, last), offset), offset);
    }

    /**
     * Join two already selected paths while retaining this path's direction. Node/edge identity,
     * never a world-space projection, determines the correspondence. Paths must overlap without
     * conflicting choices, meet at their ends, or have a single directly connecting graph edge.
     * No source binding or graph is changed and this operation does not choose through a junction.
     */
    public Merge merge(CreateRouteBinding other) {
        Objects.requireNonNull(other);
        if (!CreateTrackCompatibility.currentScope(routeBinding.graph)) {
            var current = CreateTrackCompatibility.graph(Create.RAILWAYS, routeBinding.segments.getFirst().owner.id);
            if (current == null) throw new CreateRouteBinding.RouteInvalidException();
            return new CreateRouteBinding(routeBinding.level, current, routeBinding.nodes, routeBinding.originDistance, null, routeBinding.geometryVersion).merge(other);
        }
        if (routeBinding.level != other.level || !CreateTrackCompatibility.sameNetwork(routeBinding.graph, other.graph))
            throw new IllegalArgumentException("Coupling routes must belong to the same level and track graph");
        if (!routeBinding.isValid() || !other.isValid()) throw new CreateRouteBinding.RouteInvalidException();
        if (other == routeBinding) return new Merge(routeBinding, 0, 0, false);
        if (routeBinding.graph != other.graph)
            other = new CreateRouteBinding(routeBinding.level, routeBinding.graph, other.nodes, other.originDistance, null, other.geometryVersion);

        // A shared physical edge fixes orientation and the index of the entire second path.
        // Checking the complete overlap below rejects divergent fork selections at either end.
        for (int i = 0; i < other.segments.size(); i++) {
            CreateRouteBinding.EdgePosition shared = routeBinding.edgePositions.get(other.segments.get(i).edge);
            if (shared == null) continue;
            int orientedIndex = shared.reverse() ? other.segments.size() - 1 - i : i;
            MergePlan plan = mergePlan(other, shared.reverse(), shared.segment() - orientedIndex);
            if (plan == null) throw new IllegalArgumentException("Selected coupling routes contain conflicting paths");
            return merge(other, plan);
        }

        // Without overlap, only exact endpoint adjacency is eligible. Trying both orientations
        // also supports another assembly whose forward direction opposes this route.
        MergePlan selected = null;
        for (boolean reverse : new boolean[] {false, true}) {
            TrackNode first = reverse ? other.nodes.getLast() : other.nodes.getFirst();
            TrackNode last = reverse ? other.nodes.getFirst() : other.nodes.getLast();
            if (joins(routeBinding.nodes.getLast(), first)) {
                int start = routeBinding.segments.size() + (routeBinding.nodes.getLast() == first ? 0 : 1);
                MergePlan candidate = mergePlan(other, reverse, start);
                if (candidate != null) {
                    if (selected != null) throw new IllegalArgumentException("Coupling routes have ambiguous endpoint connections");
                    selected = candidate;
                }
            }
            if (joins(last, routeBinding.nodes.getFirst())) {
                int start = -other.segments.size() - (last == routeBinding.nodes.getFirst() ? 0 : 1);
                MergePlan candidate = mergePlan(other, reverse, start);
                if (candidate != null) {
                    if (selected != null) throw new IllegalArgumentException("Coupling routes have ambiguous endpoint connections");
                    selected = candidate;
                }
            }
        }
        if (selected != null) return merge(other, selected);
        return connectGraph(other);
    }

    private record SearchStep(TrackNode previous, TrackNode node, double distance, SearchStep parent) {}
    private record SearchKey(TrackNode previous, TrackNode node) {}

    /** Only run when coupling; ordinary train ticks do not perform graph searches. */
    public Merge connectGraph(CreateRouteBinding other) {
        List<TrackNode> best = null;
        boolean bestReverse = false;
        int bestFirst = 0, bestSecond = 0;
        double bestLength = Double.POSITIVE_INFINITY;
        for (boolean prepend : new boolean[] {false, true}) for (boolean reverse : new boolean[] {false, true}) {
            List<TrackNode> left = prepend ? oriented(other.nodes, reverse) : routeBinding.nodes;
            List<TrackNode> right = prepend ? routeBinding.nodes : oriented(other.nodes, reverse);
            TrackNode from = left.getLast(), target = right.getFirst();
            var queue = new java.util.PriorityQueue<SearchStep>(java.util.Comparator.comparingDouble(SearchStep::distance));
            var visited = new java.util.HashMap<SearchKey, Double>();
            queue.add(new SearchStep(left.get(left.size() - 2), from, 0, null));
            while (!queue.isEmpty()) {
                SearchStep current = queue.remove();
                var key = new SearchKey(current.previous, current.node);
                if (current.distance >= bestLength || visited.getOrDefault(key, Double.POSITIVE_INFINITY) <= current.distance) continue;
                visited.put(key, current.distance);
                if (current.node == target) {
                    TrackEdge incoming = routeBinding.graph.getConnectionsFrom(current.previous).get(current.node);
                    TrackEdge outgoing = routeBinding.graph.getConnectionsFrom(target).get(right.get(1));
                    if (!incoming.canTravelTo(outgoing)) continue;
                    var bridge = new LinkedList<TrackNode>();
                    for (SearchStep step = current; step != null; step = step.parent) bridge.addFirst(step.node);
                    var combined = new ArrayList<>(left);
                    combined.addAll(bridge.subList(1, bridge.size()));
                    combined.addAll(right.subList(1, right.size()));
                    best = combined; bestLength = current.distance; bestReverse = reverse;
                    int rightStart = left.size() + bridge.size() - 2;
                    bestFirst = prepend ? rightStart : 0;
                    bestSecond = (prepend ? 0 : rightStart) + (reverse ? other.segments.size() : 0);
                    break;
                }
                TrackEdge incoming = routeBinding.graph.getConnectionsFrom(current.previous).get(current.node);
                for (var next : routeBinding.graph.getConnectionsFrom(current.node).entrySet()) {
                    var nextNode = CreateTrackCompatibility.canonical(routeBinding.graph, next.getKey());
                    if (nextNode == current.previous || left.contains(nextNode)
                            || nextNode != target && right.contains(nextNode)) continue;
                    TrackEdge edge = next.getValue();
                    if (!CreateRouteBinding.isOwnTrack(edge) || edge.isInterDimensional() || !incoming.canTravelTo(edge)) continue;
                    if (routeBinding.graph.getConnectionsFrom(nextNode).get(current.node) == null) continue;
                    queue.add(new SearchStep(current.node, nextNode, current.distance + edge.getLength(), current));
                }
            }
        }
        if (best == null) throw new IllegalArgumentException("两车所在轨道不连通");
        return merge(other, new MergePlan(List.copyOf(best), bestFirst, bestSecond, bestReverse));
    }

    public static List<TrackNode> oriented(List<TrackNode> input, boolean reverse) {
        return reverse ? input.reversed() : input;
    }

    public boolean joins(TrackNode from, TrackNode to) {
        return from == to || routeBinding.graph.getConnectionsFrom(from).get(to) != null;
    }

    public MergePlan mergePlan(CreateRouteBinding other, boolean reverse, int secondStart) {
        int firstSize = routeBinding.segments.size(), secondSize = other.segments.size();
        int begin = Math.min(0, secondStart), end = Math.max(firstSize, secondStart + secondSize);
        if (end - begin > CreateRouteBinding.MAX_EDGES) return null;
        var combined = new ArrayList<TrackNode>(end - begin + 1);
        Set<TrackNode> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = begin; i <= end; i++) {
            TrackNode first = i >= 0 && i <= firstSize ? routeBinding.nodes.get(i) : null;
            int secondIndex = i - secondStart;
            TrackNode second = secondIndex >= 0 && secondIndex <= secondSize
                    ? other.nodes.get(reverse ? secondSize - secondIndex : secondIndex) : null;
            if (first != null && second != null && first != second) return null;
            TrackNode node = first == null ? second : first;
            // Re-entering the same node would splice conflicting branches or introduce a loop
            // with an ambiguous endpoint correspondence, rather than extend the chosen path.
            if (node == null || !visited.add(node)) return null;
            combined.add(node);
        }
        TrackEdge incoming = null;
        for (int i = 1; i < combined.size(); i++) {
            TrackNode from = combined.get(i - 1), to = combined.get(i);
            TrackEdge edge = routeBinding.graph.getConnectionsFrom(from).get(to);
            TrackEdge backwards = routeBinding.graph.getConnectionsFrom(to).get(from);
            if (edge == null || backwards == null || !CreateRouteBinding.isOwnTrack(edge) || !CreateRouteBinding.isOwnTrack(backwards)
                    || edge.isInterDimensional() || backwards.isInterDimensional()) return null;
            if (incoming != null) {
                TrackEdge previousBackwards = routeBinding.graph.getConnectionsFrom(from).get(combined.get(i - 2));
                if (!incoming.canTravelTo(edge) || !backwards.canTravelTo(previousBackwards)) return null;
            }
            incoming = edge;
        }
        return new MergePlan(List.copyOf(combined), -begin,
                secondStart - begin + (reverse ? secondSize : 0), reverse);
    }

    public Merge merge(CreateRouteBinding other, MergePlan plan) {
        List<CreateRouteGeometry.BoundGeometry> before = new ArrayList<>(plan.firstStart);
        List<CreateRouteGeometry.BoundGeometry> after = new ArrayList<>();
        int firstEnd = plan.firstStart + routeBinding.segments.size();
        for (int i = 0; i < plan.nodes.size() - 1; i++) {
            if (i >= plan.firstStart && i < firstEnd) continue;
            TrackNode from = plan.nodes.get(i), to = plan.nodes.get(i + 1);
            CreateRouteBinding.EdgePosition source = other.edgePositions.get(routeBinding.graph.getConnectionsFrom(from).get(to));
            CreateRouteGeometry.BoundGeometry geometry = source != null && !source.reverse()
                    ? other.segments.get(source.segment()) : new CreateRouteGeometry.BoundGeometry(routeBinding.graph, from, to, routeBinding.geometryVersion);
            (i < plan.firstStart ? before : after).add(geometry);
        }
        TrackRouteView joined = routeBinding.route.extendedDirected(mapped(before), mapped(after));
        double firstOffset = nodeDistance(joined, plan.firstStart, plan.nodes.size() - 1);
        double secondOffset = nodeDistance(joined, plan.secondOrigin, plan.nodes.size() - 1);
        // The two directed representations must have the same measured lengths. The comparison
        // allows only floating-point arc-table roundoff; it never substitutes nearest projection.
        for (int i = 0; i <= other.segments.size(); i++) {
            double oldDistance = nodeDistance(other.route, i, other.segments.size());
            int index = plan.secondOrigin + (plan.reverseSecond ? -i : i);
            double actual = nodeDistance(joined, index, plan.nodes.size() - 1);
            double mapped = secondOffset + (plan.reverseSecond ? -oldDistance : oldDistance);
            if (Math.abs(actual - mapped) > 1e-7)
                throw new IllegalArgumentException("Directed coupling routes have inconsistent measured edge lengths");
        }
        var geometry = new ArrayList<CreateRouteGeometry.BoundGeometry>(plan.nodes.size() - 1);
        geometry.addAll(before); geometry.addAll(routeBinding.segments); geometry.addAll(after);
        CreateRouteBinding binding = before.isEmpty() && after.isEmpty() ? routeBinding
                : new CreateRouteBinding(routeBinding, plan.nodes, geometry, joined, firstOffset);
        return new Merge(binding, firstOffset, secondOffset, plan.reverseSecond);
    }

    public static double nodeDistance(TrackRouteView route, int node, int edges) {
        return node == edges ? route.length() : route.distanceAt(node, 0);
    }

    public static List<TrackRouteDirectedSegmentView> mapped(List<CreateRouteGeometry.BoundGeometry> segments) {
        return segments.stream().map(segment -> HexieBridges.railway().createTrackRouteDirectedSegment(segment, segment.reversed)).toList();
    }
}
