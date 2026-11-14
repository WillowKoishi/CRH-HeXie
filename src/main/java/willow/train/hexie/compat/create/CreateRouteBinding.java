package willow.train.hexie.compat.create;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackGraphBounds;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.compat.create.track.CreateRailwayBackend;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.vehicle.assembly.VehicleAssemblyPreparer;
import willow.train.hexie.vehicle.kinematics.VehicleConstraintSolver;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;
import willow.train.hexie.vehicle.runtime.VehicleRuntime;
import willow.train.hexie.vehicle.track.TrackGeometry;
import willow.train.hexie.vehicle.track.TrackPath;
import willow.train.hexie.vehicle.track.TrackRoute;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/** A saved, explicitly directed Create route. All access is on the server's owning thread. */
public final class CreateRouteBinding {
    public static final class RouteInvalidException extends IllegalStateException {
        public RouteInvalidException() { super("Selected track edge changed; route must be rebound"); }
    }

    private static final int MAX_EDGES = 4096;
    private static final int GEOMETRY_VERSION = 5;
    // Native bounds objects are replaced on graph edits. Weak keys retain neither old graphs nor worlds.
    private static final Map<TrackGraphBounds, Map<Long, List<BezierConnection>>> IMPORT_CURVES = new WeakHashMap<>();
    private final ServerLevel level;
    private final TrackGraph graph;
    private final List<TrackNode> nodes;
    private final List<BoundGeometry> segments;
    private final TrackRoute route;
    private final double originDistance;
    private final int geometryVersion;
    private final Map<TrackEdge, EdgePosition> edgePositions = new IdentityHashMap<>();

    /** Preview is at its node2, facing outward. Choosing never advances a live Create point. */
    @FunctionalInterface
    public interface RouteChoice {
        Map.Entry<TrackNode, TrackEdge> choose(TrackGraph graph, TravellingPoint preview,
                                             boolean extendingForward,
                                             List<Map.Entry<TrackNode, TrackEdge>> candidates);
    }

    /** Add distanceOffset to every old route cursor when replacing a binding with this result. */
    public record Extension(CreateRouteBinding binding, double distanceOffset) {}
    /** First cursors become s + firstOffset; second cursors become (+/-s) + secondOffset. */
    public record Merge(CreateRouteBinding route, double firstOffset, double secondOffset, boolean reverseSecond) {}
    private record EdgePosition(int segment, boolean reverse) {}
    private record MergePlan(List<TrackNode> nodes, int firstStart, int secondOrigin, boolean reverseSecond) {}

    private CreateRouteBinding(ServerLevel level, TrackGraph graph, List<TrackNode> nodes,
                               double originDistance, Vec3 initialPosition) {
        this(level, graph, nodes, originDistance, initialPosition, GEOMETRY_VERSION);
    }

    private CreateRouteBinding(ServerLevel level, TrackGraph graph, List<TrackNode> nodes,
                               double originDistance, Vec3 initialPosition, int geometryVersion) {
        this.level = Objects.requireNonNull(level);
        this.graph = CreateTrackCompatibility.routeGraph(Objects.requireNonNull(graph));
        graph = this.graph;
        var selectedGraph = graph;
        nodes = nodes.stream().map(node -> CreateTrackCompatibility.canonical(selectedGraph, node)).toList();
        this.nodes = List.copyOf(nodes);
        this.geometryVersion = geometryVersion;
        if (nodes.size() < 2 || nodes.size() > MAX_EDGES + 1)
            throw new IllegalArgumentException("Route must contain between 1 and " + MAX_EDGES + " edges");
        List<BoundGeometry> geometry = new ArrayList<>(nodes.size() - 1);
        for (TrackNode node : nodes) {
            if (!level.dimension().equals(node.getLocation().getDimension())
                    || !CreateTrackCompatibility.owns(graph, node))
                throw new IllegalArgumentException("Route node is not in this level and graph");
        }
        for (int i = 1; i < nodes.size(); i++)
            geometry.add(new BoundGeometry(graph, nodes.get(i - 1), nodes.get(i), geometryVersion));
        segments = List.copyOf(geometry);
        route = TrackRoute.directed(mapped(segments));
        indexEdges();
        this.originDistance = initialPosition == null ? originDistance : project(initialPosition);
        if (!Double.isFinite(this.originDistance) || this.originDistance < 0 || this.originDistance > route.length())
            throw new IllegalArgumentException("Origin is outside the selected route");
    }

    private CreateRouteBinding(CreateRouteBinding previous, List<TrackNode> nodes,
                               List<BoundGeometry> segments, TrackRoute route, double offset) {
        this.level = previous.level;
        this.graph = previous.graph;
        this.geometryVersion = previous.geometryVersion;
        this.nodes = List.copyOf(nodes);
        this.segments = List.copyOf(segments);
        this.route = route;
        // The initial assembly seed can leave a moving route window. Live carriage cursors
        // receive the exact offset separately; the seed is only used by initial assembly.
        this.originDistance = Math.clamp(previous.originDistance + offset, 0, route.length());
        indexEdges();
    }

    private void indexEdges() {
        for (int i = 0; i < segments.size(); i++) {
            BoundGeometry segment = segments.get(i);
            if (edgePositions.put(segment.edge, new EdgePosition(i, false)) != null)
                throw new IllegalArgumentException("A selected route cannot repeat an edge");
            TrackEdge reverse = graph.getConnectionsFrom(segment.to).get(segment.from);
            if (reverse == null || edgePositions.put(reverse, new EdgePosition(i, true)) != null)
                throw new IllegalArgumentException("Route needs unique bidirectional connections");
        }
    }

    /** Build a new directed route from a precise Create point, choosing forks only through choice. */
    public static CreateRouteBinding fromPoint(ServerLevel level, TrackGraph graph, TravellingPoint anchor,
                                               boolean forward, RouteChoice choice) {
        graph = CreateTrackCompatibility.routeGraph(graph);
        anchor = CreateTrackCompatibility.onGraph(graph, anchor);
        parameterOf(anchor);
        TrackNode first = CreateTrackCompatibility.canonical(graph, anchor.node1);
        TrackNode second = CreateTrackCompatibility.canonical(graph, anchor.node2);
        LinkedList<TrackNode> nodes = new LinkedList<>(forward ? List.of(first, second) : List.of(second, first));
        Set<TrackNode> visited = new HashSet<>(nodes);
        extend(graph, nodes, visited, false, choice);
        extend(graph, nodes, visited, true, choice);
        CreateRouteBinding provisional = new CreateRouteBinding(level, graph, nodes, 0, null);
        return new CreateRouteBinding(provisional, nodes, provisional.segments, provisional.route,
                provisional.distanceOf(anchor));
    }

    /** Reconstruct the occupied native corridor without making another steering decision. */
    public static CreateRouteBinding fromPoints(ServerLevel level, TrackGraph graph, TravellingPoint anchor,
                                                List<TravellingPoint> occupied) {
        return fromPoints(level, graph, anchor, occupied, true);
    }

    static CreateRouteBinding fromPoints(ServerLevel level, TrackGraph graph, TravellingPoint anchor,
                                         List<TravellingPoint> occupied, boolean alongPoints) {
        try {
            graph = CreateTrackCompatibility.routeGraph(graph);
            anchor = CreateTrackCompatibility.onGraph(graph, anchor);
            var selected = new CreateRouteBinding(level, graph,
                    alongPoints ? List.of(anchor.node1, anchor.node2) : List.of(anchor.node2, anchor.node1), 0, null);
            selected.distanceOf(anchor);
            for (var nativePoint : occupied) {
                var point = CreateTrackCompatibility.onGraph(graph, nativePoint);
                if (!selected.edgePositions.containsKey(point.edge)) {
                    var segment = new CreateRouteBinding(level, graph, List.of(point.node1, point.node2), 0, null);
                    segment.distanceOf(point);
                    selected = selected.merge(segment).route();
                }
                selected.distanceOf(point);
            }
            return new CreateRouteBinding(selected, selected.nodes, selected.segments, selected.route,
                    selected.distanceOf(anchor));
        } catch (IllegalArgumentException changed) {
            // A partially migrated native graph is unavailable geometry, not a fatal server tick.
            var unavailable = new RouteInvalidException();
            unavailable.initCause(changed);
            throw unavailable;
        }
    }

    /** Called when control has selected new exits, not every tick; old edge arc tables are reused. */
    public Extension extend(boolean forward, RouteChoice choice) {
        if (!CreateTrackCompatibility.currentScope(graph)) {
            var current = CreateTrackCompatibility.graph(Create.RAILWAYS,
                    (forward ? segments.getLast() : segments.getFirst()).owner.id);
            if (current == null) throw new RouteInvalidException();
            return new CreateRouteBinding(level, current, nodes, originDistance, null, geometryVersion).extend(forward, choice);
        }
        if (!isValid()) throw new RouteInvalidException();
        LinkedList<TrackNode> extendedNodes = new LinkedList<>(nodes);
        extend(graph, extendedNodes, new HashSet<>(nodes), forward, choice);
        int added = extendedNodes.size() - nodes.size();
        if (added == 0) return new Extension(this, 0);
        List<BoundGeometry> extra = new ArrayList<>(added);
        int first = forward ? nodes.size() - 1 : 0;
        for (int i = first; i < first + added; i++)
            extra.add(new BoundGeometry(graph, extendedNodes.get(i), extendedNodes.get(i + 1), geometryVersion));
        TrackRoute extended = route.extendedDirected(forward ? List.of() : mapped(extra), forward ? mapped(extra) : List.of());
        double offset = forward ? 0 : extended.distanceAt(added, 0);
        List<BoundGeometry> geometry = new ArrayList<>(extendedNodes.size() - 1);
        if (!forward) geometry.addAll(extra);
        geometry.addAll(segments);
        if (forward) geometry.addAll(extra);
        return new Extension(new CreateRouteBinding(this, extendedNodes, geometry, extended, offset), offset);
    }

    /** Release only unoccupied edges behind travel when a loop would revisit the old route. */
    public Extension extend(boolean forward, RouteChoice choice, double minimumOccupied, double maximumOccupied) {
        if (!Double.isFinite(minimumOccupied) || !Double.isFinite(maximumOccupied)
                || minimumOccupied > maximumOccupied)
            throw new IllegalArgumentException("Invalid occupied route interval");
        // Release the unused history before checking backend objects: a removed node behind
        // the entire train must not veto movement on its still-present occupied track.
        int first = forward ? route.segmentIndex(boundDistance(minimumOccupied)) : 0;
        int end = forward ? segments.size() : route.segmentIndex(boundDistance(maximumOccupied)) + 1;
        if (first == 0 && end == segments.size()) return extend(forward, choice);
        double shift = -route.distanceAt(first, 0);
        var window = new CreateRouteBinding(this, nodes.subList(first, end + 1),
                segments.subList(first, end), route.slice(first, end), shift);
        var continued = window.extend(forward, choice);
        return new Extension(continued.binding, shift + continued.distanceOffset);
    }

    /** Reconsider cached forks before entry, while retaining every occupied wheel/support edge. */
    Extension reselectAhead(boolean forward, RouteChoice choice, double distance, double lookAhead,
                            double minimumOccupied, double maximumOccupied) {
        int index = route.segmentIndex(boundDistance(distance));
        int step = forward ? 1 : -1;
        // Native travel chooses only after crossing a node. A wheel exactly at the node
        // may still change its exit, although segmentIndex already names the next edge.
        int next = forward && (index == 0 || route.distanceAt(index, 0) != distance) ? index + 1 : index;
        for (int junction = next;
                junction > 0 && junction < segments.size(); junction += step) {
            double at = route.distanceAt(junction, 0);
            if (Math.abs(at - distance) > lookAhead) break;
            // Protect the whole shared corridor, including gaps between separate consists.
            if (forward ? at < maximumOccupied : at > minimumOccupied) return new Extension(this, 0);
            TrackNode previous = nodes.get(junction - step), current = nodes.get(junction);
            TrackEdge incoming = graph.getConnectionsFrom(previous).get(current);
            if (incoming == null) throw new RouteInvalidException();
            var candidates = continuations(graph, previous, current, incoming);
            if (candidates.size() < 2) continue;
            var selected = choice.choose(graph, CreateTrackCompatibility.point(graph, previous, current, incoming,
                    incoming.getLength(), false), forward, List.copyOf(candidates));
            if (selected != null && candidates.stream().noneMatch(candidate -> candidate.getKey() == selected.getKey()
                    && candidate.getValue() == selected.getValue()))
                throw new IllegalArgumentException("Route choice returned an unavailable exit");
            if (selected != null && selected.getKey() == nodes.get(junction + step)) continue;

            // Keep the incoming edge, discard the obsolete future and release unused history
            // so a return around a loop can revisit its old nodes. Reuse all occupied arc maps.
            int first = forward ? Math.min(route.segmentIndex(boundDistance(minimumOccupied)), junction - 1) : junction;
            int end = forward ? junction : route.segmentIndex(boundDistance(maximumOccupied)) + 1;
            double shift = -route.distanceAt(first, 0);
            var window = new CreateRouteBinding(this, nodes.subList(first, end + 1),
                    segments.subList(first, end), route.slice(first, end), shift);
            boolean[] firstChoice = {true};
            var continued = window.extend(forward, (network, preview, extendingForward, choices) -> {
                // Native scouts consume their copied itinerary when choosing. Do not consume
                // this same fork twice when extending the newly truncated route.
                if (firstChoice[0]) { firstChoice[0] = false; return selected; }
                return choice.choose(network, preview, extendingForward, choices);
            });
            return new Extension(continued.binding, shift + continued.distanceOffset);
        }
        return new Extension(this, 0);
    }

    /** Refresh only sampled/next-tick edges, not distant history or future selected track. */
    public Extension refresh(double minimumNeeded, double maximumNeeded) {
        int first = route.segmentIndex(boundDistance(minimumNeeded));
        int end = route.segmentIndex(boundDistance(maximumNeeded)) + 1;
        boolean current = CreateTrackCompatibility.currentScope(graph);
        for (int i = first; i < end; i++) current &= segments.get(i).valid();
        if (current) return new Extension(this, 0);
        var currentGraph = CreateTrackCompatibility.graph(Create.RAILWAYS, segments.get(first).owner.id);
        var resolved = refreshNodes(currentGraph, first, end);
        if (resolved == null) {
            // A graph merge/split may retire its old UUID while retaining the occupied track.
            // Match the selected corridor by exact nodes and edges, never by nearby geometry.
            currentGraph = null;
            for (var registered : Create.RAILWAYS.trackNetworks.values()) {
                var candidate = CreateTrackCompatibility.routeGraph(registered);
                var candidateNodes = refreshNodes(candidate, first, end);
                if (candidateNodes == null) continue;
                if (currentGraph != null && !CreateTrackCompatibility.sameNetwork(currentGraph, candidate))
                    throw new RouteInvalidException();
                currentGraph = candidate;
                resolved = candidateNodes;
            }
            if (resolved == null) throw new RouteInvalidException();
        }
        double shift = -route.distanceAt(first, 0);
        var refreshed = new CreateRouteBinding(level, currentGraph, resolved, 0, null, geometryVersion);
        return new Extension(refreshed, shift);
    }

    private List<TrackNode> refreshNodes(TrackGraph currentGraph, int first, int end) {
        if (currentGraph == null) return null;
        var resolved = new ArrayList<TrackNode>(end - first + 1);
        for (int i = first; i <= end; i++) {
            var node = currentGraph.locateNode(nodes.get(i).getLocation());
            if (node == null) return null;
            if (!resolved.isEmpty()) {
                var previous = resolved.getLast();
                if (currentGraph.getConnectionsFrom(previous).get(node) == null
                        || currentGraph.getConnectionsFrom(node).get(previous) == null)
                    return null;
            }
            resolved.add(node);
        }
        return resolved;
    }

    /** Build the coupling corridor from occupied track, not obsolete choices outside the trains. */
    public Merge connectForCoupling(CreateRouteBinding other, double firstMin, double firstMax,
            double secondMin, double secondMax) {
        Extension a = occupied(firstMin, firstMax), b = other.occupied(secondMin, secondMax);
        Merge joined = a.binding.merge(b.binding);
        return new Merge(joined.route, joined.firstOffset + a.distanceOffset,
                joined.secondOffset + (joined.reverseSecond ? -b.distanceOffset : b.distanceOffset), joined.reverseSecond);
    }

    private Extension occupied(double min, double max) {
        int first = route.segmentIndex(boundDistance(min));
        int last = route.segmentIndex(boundDistance(max)) + 1;
        double offset = -route.distanceAt(first, 0);
        return new Extension(new CreateRouteBinding(this, nodes.subList(first, last + 1),
                segments.subList(first, last), route.slice(first, last), offset), offset);
    }

    /**
     * Join two already selected paths while retaining this path's direction. Node/edge identity,
     * never a world-space projection, determines the correspondence. Paths must overlap without
     * conflicting choices, meet at their ends, or have a single directly connecting graph edge.
     * No source binding or graph is changed and this operation does not choose through a junction.
     */
    public Merge merge(CreateRouteBinding other) {
        Objects.requireNonNull(other);
        if (!CreateTrackCompatibility.currentScope(graph)) {
            var current = CreateTrackCompatibility.graph(Create.RAILWAYS, segments.getFirst().owner.id);
            if (current == null) throw new RouteInvalidException();
            return new CreateRouteBinding(level, current, nodes, originDistance, null, geometryVersion).merge(other);
        }
        if (level != other.level || !CreateTrackCompatibility.sameNetwork(graph, other.graph))
            throw new IllegalArgumentException("Coupling routes must belong to the same level and track graph");
        if (!isValid() || !other.isValid()) throw new RouteInvalidException();
        if (other == this) return new Merge(this, 0, 0, false);
        if (graph != other.graph)
            other = new CreateRouteBinding(level, graph, other.nodes, other.originDistance, null, other.geometryVersion);

        // A shared physical edge fixes orientation and the index of the entire second path.
        // Checking the complete overlap below rejects divergent fork selections at either end.
        for (int i = 0; i < other.segments.size(); i++) {
            EdgePosition shared = edgePositions.get(other.segments.get(i).edge);
            if (shared == null) continue;
            int orientedIndex = shared.reverse ? other.segments.size() - 1 - i : i;
            MergePlan plan = mergePlan(other, shared.reverse, shared.segment - orientedIndex);
            if (plan == null) throw new IllegalArgumentException("Selected coupling routes contain conflicting paths");
            return merge(other, plan);
        }

        // Without overlap, only exact endpoint adjacency is eligible. Trying both orientations
        // also supports another assembly whose forward direction opposes this route.
        MergePlan selected = null;
        for (boolean reverse : new boolean[] {false, true}) {
            TrackNode first = reverse ? other.nodes.getLast() : other.nodes.getFirst();
            TrackNode last = reverse ? other.nodes.getFirst() : other.nodes.getLast();
            if (joins(nodes.getLast(), first)) {
                int start = segments.size() + (nodes.getLast() == first ? 0 : 1);
                MergePlan candidate = mergePlan(other, reverse, start);
                if (candidate != null) {
                    if (selected != null) throw new IllegalArgumentException("Coupling routes have ambiguous endpoint connections");
                    selected = candidate;
                }
            }
            if (joins(last, nodes.getFirst())) {
                int start = -other.segments.size() - (last == nodes.getFirst() ? 0 : 1);
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
    private Merge connectGraph(CreateRouteBinding other) {
        List<TrackNode> best = null;
        boolean bestReverse = false;
        int bestFirst = 0, bestSecond = 0;
        double bestLength = Double.POSITIVE_INFINITY;
        for (boolean prepend : new boolean[] {false, true}) for (boolean reverse : new boolean[] {false, true}) {
            List<TrackNode> left = prepend ? oriented(other.nodes, reverse) : nodes;
            List<TrackNode> right = prepend ? nodes : oriented(other.nodes, reverse);
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
                    TrackEdge incoming = graph.getConnectionsFrom(current.previous).get(current.node);
                    TrackEdge outgoing = graph.getConnectionsFrom(target).get(right.get(1));
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
                TrackEdge incoming = graph.getConnectionsFrom(current.previous).get(current.node);
                for (var next : graph.getConnectionsFrom(current.node).entrySet()) {
                    var nextNode = CreateTrackCompatibility.canonical(graph, next.getKey());
                    if (nextNode == current.previous || left.contains(nextNode)
                            || nextNode != target && right.contains(nextNode)) continue;
                    TrackEdge edge = next.getValue();
                    if (!isOwnTrack(edge) || edge.isInterDimensional() || !incoming.canTravelTo(edge)) continue;
                    if (graph.getConnectionsFrom(nextNode).get(current.node) == null) continue;
                    queue.add(new SearchStep(current.node, nextNode, current.distance + edge.getLength(), current));
                }
            }
        }
        if (best == null) throw new IllegalArgumentException("两车所在轨道不连通");
        return merge(other, new MergePlan(List.copyOf(best), bestFirst, bestSecond, bestReverse));
    }

    private static List<TrackNode> oriented(List<TrackNode> input, boolean reverse) {
        return reverse ? input.reversed() : input;
    }

    private boolean joins(TrackNode from, TrackNode to) {
        return from == to || graph.getConnectionsFrom(from).get(to) != null;
    }

    private MergePlan mergePlan(CreateRouteBinding other, boolean reverse, int secondStart) {
        int firstSize = segments.size(), secondSize = other.segments.size();
        int begin = Math.min(0, secondStart), end = Math.max(firstSize, secondStart + secondSize);
        if (end - begin > MAX_EDGES) return null;
        var combined = new ArrayList<TrackNode>(end - begin + 1);
        Set<TrackNode> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = begin; i <= end; i++) {
            TrackNode first = i >= 0 && i <= firstSize ? nodes.get(i) : null;
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
            TrackEdge edge = graph.getConnectionsFrom(from).get(to);
            TrackEdge backwards = graph.getConnectionsFrom(to).get(from);
            if (edge == null || backwards == null || !isOwnTrack(edge) || !isOwnTrack(backwards)
                    || edge.isInterDimensional() || backwards.isInterDimensional()) return null;
            if (incoming != null) {
                TrackEdge previousBackwards = graph.getConnectionsFrom(from).get(combined.get(i - 2));
                if (!incoming.canTravelTo(edge) || !backwards.canTravelTo(previousBackwards)) return null;
            }
            incoming = edge;
        }
        return new MergePlan(List.copyOf(combined), -begin,
                secondStart - begin + (reverse ? secondSize : 0), reverse);
    }

    private Merge merge(CreateRouteBinding other, MergePlan plan) {
        List<BoundGeometry> before = new ArrayList<>(plan.firstStart);
        List<BoundGeometry> after = new ArrayList<>();
        int firstEnd = plan.firstStart + segments.size();
        for (int i = 0; i < plan.nodes.size() - 1; i++) {
            if (i >= plan.firstStart && i < firstEnd) continue;
            TrackNode from = plan.nodes.get(i), to = plan.nodes.get(i + 1);
            EdgePosition source = other.edgePositions.get(graph.getConnectionsFrom(from).get(to));
            BoundGeometry geometry = source != null && !source.reverse
                    ? other.segments.get(source.segment) : new BoundGeometry(graph, from, to, geometryVersion);
            (i < plan.firstStart ? before : after).add(geometry);
        }
        TrackRoute joined = route.extendedDirected(mapped(before), mapped(after));
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
        var geometry = new ArrayList<BoundGeometry>(plan.nodes.size() - 1);
        geometry.addAll(before); geometry.addAll(segments); geometry.addAll(after);
        CreateRouteBinding binding = before.isEmpty() && after.isEmpty() ? this
                : new CreateRouteBinding(this, plan.nodes, geometry, joined, firstOffset);
        return new Merge(binding, firstOffset, secondOffset, plan.reverseSecond);
    }

    private static double nodeDistance(TrackRoute route, int node, int edges) {
        return node == edges ? route.length() : route.distanceAt(node, 0);
    }

    private static List<TrackRoute.DirectedSegment> mapped(List<BoundGeometry> segments) {
        return segments.stream().map(segment -> new TrackRoute.DirectedSegment(segment, segment.reversed)).toList();
    }

    /** Upgrade old geometry through each support's exact edge parameter, preserving saved frames until remap. */
    CreateRouteBinding canonicalized() {
        if (geometryVersion >= GEOMETRY_VERSION) return this;
        var upgraded = new CreateRouteBinding(level, graph, nodes, 0, null, GEOMETRY_VERSION);
        return new CreateRouteBinding(upgraded, nodes, upgraded.segments, upgraded.route,
                upgraded.distanceOf(pointAt(originDistance)));
    }

    /** Bind the station edge and cover at least the first assembly support. */
    public static CreateRouteBinding fromStation(StationBlockEntity station, Vec3 firstBogeyWorld,
                                                 Direction forward) {
        return fromStation(station, firstBogeyWorld, forward, List.of());
    }

    /**
     * Cover the captured supports along the station's straight assembly strip. A branch within
     * that strip follows the unique straight continuation, just like Create's assembly scan.
     * Beyond the supports, junctions remain undecided for the normal train controls.
     */
    public static CreateRouteBinding fromStation(StationBlockEntity station, Vec3 firstBogeyWorld,
                                                 Direction forward, List<BlockPos> supportPositions) {
        if (!(station.getLevel() instanceof ServerLevel level) || !station.edgePoint.hasValidTrack())
            throw new IllegalArgumentException("Station needs a server level and a valid target track");
        if (!forward.getAxis().isHorizontal()) throw new IllegalArgumentException("Assembly direction must be horizontal");
        var location = station.edgePoint.determineGraphLocation();
        if (location == null) throw new IllegalArgumentException("Station track is not in a graph");
        return fromAssemblyLocation(level, location, firstBogeyWorld, Vec3.atLowerCornerOf(forward.getNormal()),
                supportPositions.stream().map(Vec3::atCenterOf).toList());
    }

    /**
     * Bind a copied body at its final pasted pose, never to its former graph or a nearby parallel line.
     * The caller has already restored its own structure. No match leaves that structure unbound.
     */
    static CreateRouteBinding forImportedCarriage(SnapshotCarriageEntity carrier,
                                                 VehicleAssemblyPreparer.Prepared prepared) {
        if (!(carrier.level() instanceof ServerLevel level) || prepared.carriages().size() != 1)
            throw new IllegalArgumentException("Import needs one carriage in a server level");
        var definition = prepared.carriages().getFirst().definition();
        Vec3 origin = carrier.position().add(0, .5, 0);
        Vec3 direction = importedPoint(carrier, new Point(0, 0, -1)).subtract(origin).normalize();
        var supports = definition.bogeys().stream().map(bogey -> {
            var body = bogey.mount().bodyPoint();
            var track = bogey.mount().trackPivot();
            return importedPoint(carrier, new Point(body.x() - track.x(), body.y() - track.y(), body.z() - track.z()));
        }).toList();
        var visited = new HashSet<BlockPos>();
        for (Vec3 support : supports) {
            BlockPos near = BlockPos.containing(support);
            // The neighbouring cells cover raised/canted rails and mount offsets at cell edges.
            // Exact pose validation below rejects a neighbour that is a different physical line.
            for (BlockPos mutable : BlockPos.betweenClosed(near.offset(-1, -1, -1), near.offset(1, 1, 1))) {
                BlockPos position = mutable.immutable();
                if (!visited.add(position) || !level.hasChunkAt(position)) continue;
                var state = level.getBlockState(position);
                if (!(state.getBlock() instanceof ITrackBlock track)) continue;
                var axis = track.getNearestTrackAxis(level, position, state, direction);
                var location = TrackGraphHelper.getGraphLocationAt(level, position, axis.getSecond(), axis.getFirst());
                if (location == null) continue;
                TrackGraph graph = CreateTrackCompatibility.routeGraph(location.graph);
                TrackNode first = graph.locateNode(location.edge.getFirst());
                TrackNode second = graph.locateNode(location.edge.getSecond());
                if (first == null || second == null || graph.getConnectionsFrom(first) == null) continue;
                TrackEdge edge = graph.getConnectionsFrom(first).get(second);
                if (edge == null || !isOwnTrack(edge) || edge.isInterDimensional()) continue;
                var tangent = new CreateTrackGeometry(graph, edge).sample(
                        Math.clamp(location.position / edge.getLength(), 0, 1)).tangent();
                if (Math.abs(tangent.x() * direction.x + tangent.y() * direction.y + tangent.z() * direction.z) < .5)
                    continue;
                CreateRouteBinding candidate = fromAssemblyLocation(level, location, origin, direction, supports);
                var fitted = fitImportedPose(carrier, prepared, candidate, origin, direction);
                if (fitted != null) return fitted;
            }
        }
        // Curve interiors have no ITrackBlock. Reuse native graph bounds, indexed once per
        // bounds revision, so an unbound body does not rescan the railway on each retry.
        for (TrackGraph physical : Create.RAILWAYS.trackNetworks.values()) {
            var indexed = IMPORT_CURVES.computeIfAbsent(physical.getBounds(level), CreateRouteBinding::indexImportCurves);
            var curves = new HashSet<BezierConnection>();
            for (Vec3 support : supports)
                curves.addAll(indexed.getOrDefault(ChunkPos.asLong(BlockPos.containing(support)), List.of()));
            for (var curve : curves) {
                if (supports.stream().noneMatch(curve.getBounds()::contains)) continue;
                var start = curve.bePositions.getFirst();
                TrackGraphLocation location = level.hasChunkAt(start)
                        ? TrackGraphHelper.getBezierGraphLocationAt(level, start, Direction.AxisDirection.NEGATIVE,
                                new BezierTrackPointLocation(curve.bePositions.getSecond(), 0)) : null;
                if (location == null) {
                    // The graph retains a curve even when its endpoint blocks are unloaded.
                    var firstLocation = new TrackNodeLocation(curve.starts.getFirst()).in(level);
                    var secondLocation = new TrackNodeLocation(curve.starts.getSecond()).in(level);
                    if (curve.smoothing != null) {
                        firstLocation.yOffsetPixels = curve.smoothing.getFirst();
                        secondLocation.yOffsetPixels = curve.smoothing.getSecond();
                    }
                    var first = physical.locateNode(firstLocation);
                    var second = physical.locateNode(secondLocation);
                    if (first == null || second == null) continue;
                    var edge = physical.getConnectionsFrom(first).get(second);
                    if (edge == null || edge.getTurn() != curve || !isOwnTrack(edge)) continue;
                    location = new TrackGraphLocation();
                    location.graph = physical;
                    location.edge = Couple.create(firstLocation, secondLocation);
                }
                var graph = CreateTrackCompatibility.routeGraph(location.graph);
                var first = graph.locateNode(location.edge.getFirst());
                var second = graph.locateNode(location.edge.getSecond());
                if (first == null || second == null) continue;
                var edge = graph.getConnectionsFrom(first).get(second);
                if (edge == null || !isOwnTrack(edge)) continue;
                var segment = new CreateRouteBinding(level, graph, List.of(first, second), 0, null);
                location.position = segment.pointAt(segment.project(origin)).position;
                var tangent = new CreateTrackGeometry(graph, edge).sample(
                        Math.clamp(location.position / edge.getLength(), 0, 1)).tangent();
                if (Math.abs(tangent.x() * direction.x + tangent.y() * direction.y + tangent.z() * direction.z) < .5)
                    continue;
                var candidate = fromAssemblyLocation(level, location, origin, direction, supports);
                var fitted = fitImportedPose(carrier, prepared, candidate, origin, direction);
                if (fitted != null) return fitted;
            }
        }
        return null;
    }

    private static Map<Long, List<BezierConnection>> indexImportCurves(TrackGraphBounds bounds) {
        var indexed = new HashMap<Long, List<BezierConnection>>();
        for (var curve : bounds.beziers) {
            var box = curve.getBounds();
            int minX = net.minecraft.util.Mth.floor(box.minX) >> 4, maxX = net.minecraft.util.Mth.floor(box.maxX) >> 4;
            int minZ = net.minecraft.util.Mth.floor(box.minZ) >> 4, maxZ = net.minecraft.util.Mth.floor(box.maxZ) >> 4;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                indexed.computeIfAbsent(ChunkPos.asLong(x, z), ignored -> new ArrayList<>()).add(curve);
        }
        return indexed;
    }

    /** Convert the runtime's north-facing attachment coordinates to the carrier's source-block frame. */
    private static Vec3 importedPoint(SnapshotCarriageEntity carrier, Point point) {
        Vec3 local = switch (carrier.originalForward()) {
            case NORTH -> new Vec3(point.x(), point.y(), point.z());
            case EAST -> new Vec3(-point.z(), point.y(), point.x());
            case SOUTH -> new Vec3(-point.x(), point.y(), -point.z());
            case WEST -> new Vec3(point.z(), point.y(), -point.x());
            default -> throw new IllegalArgumentException("Carriage source direction must be horizontal");
        };
        return carrier.toGlobalVector(local.add(.5, .5, .5), 1);
    }

    private static CreateRouteBinding fitImportedPose(SnapshotCarriageEntity carrier,
            VehicleAssemblyPreparer.Prepared prepared, CreateRouteBinding route, Vec3 origin, Vec3 direction) {
        double previousDistance = Double.NaN, previousError = Double.NaN;
        // Rigid bodies span chords, so their reference mileage is not generally the projection
        // of the body centre on a curve. Correct only this import seed using the existing solver.
        for (int step = 0; step < 12; step++) {
            var placement = importedPlacement(prepared, route);
            if (placement == null) return null;
            if (matchesImportedPose(carrier, prepared, placement)) return route;
            var body = placement.bodies().get(prepared.carriages().getFirst().definition().id()).position();
            double error = new Vec3(body.x(), body.y(), body.z()).subtract(origin).dot(direction);
            if (Math.abs(error) < 1e-9) return null;
            double distance = route.originDistance();
            double adjustment = error;
            if (Double.isFinite(previousError) && Math.abs(error - previousError) > 1e-12)
                adjustment = error * (distance - previousDistance) / (error - previousError);
            double next = distance - adjustment;
            if (!Double.isFinite(next) || next < route.path().minimumDistance()
                    || next > route.path().maximumDistance()) return null;
            previousDistance = distance;
            previousError = error;
            route = new CreateRouteBinding(route, route.nodes, route.segments, route.route, next - distance);
        }
        return null;
    }

    private static VehicleConstraintSolver.Solution importedPlacement(VehicleAssemblyPreparer.Prepared prepared,
                                                                       CreateRouteBinding route) {
        var imported = prepared.carriages().getFirst();
        var definition = imported.definition();
        double distance = route.originDistance() + imported.initialDistance();
        for (var bogey : definition.bogeys()) {
            double support = distance + bogey.offset() + bogey.mount().trackPivot().z();
            if (support < route.path().minimumDistance() || support > route.path().maximumDistance()) return null;
        }
        var runtime = new VehicleRuntime(route.path());
        try { runtime.addCarriage(definition, distance, 0); }
        catch (VehicleConstraintSolver.ConstraintFailure noPlacement) { return null; }
        return runtime.placement();
    }

    private static boolean matchesImportedPose(SnapshotCarriageEntity carrier,
            VehicleAssemblyPreparer.Prepared prepared, VehicleConstraintSolver.Solution placement) {
        var definition = prepared.carriages().getFirst().definition();
        var body = placement.bodies().get(definition.id());
        if (body == null) return false;
        // Same geometric tolerance as the normal assembly's original-pose check.
        double toleranceSquared = 1e-8;
        var origin = body.position();
        if (new Vec3(origin.x(), origin.y(), origin.z()).distanceToSqr(carrier.position().add(0, .5, 0)) > toleranceSquared)
            return false;
        for (Point axis : List.of(new Point(1, 0, 0), new Point(0, 1, 0), new Point(0, 0, -1))) {
            var point = body.transform(axis);
            if (new Vec3(point.x(), point.y(), point.z()).distanceToSqr(importedPoint(carrier, axis)) > toleranceSquared)
                return false;
        }
        for (var bogey : definition.bogeys()) {
            var support = placement.bogeys().get(bogey.id());
            if (support == null) return false;
            var attachment = support.attachment();
            if (new Vec3(attachment.x(), attachment.y(), attachment.z()).distanceToSqr(
                    importedPoint(carrier, bogey.mount().bodyPoint())) > toleranceSquared) return false;
            if (support.offRailOffset().dot(support.offRailOffset()) > toleranceSquared) return false;
        }
        return true;
    }

    private static CreateRouteBinding fromAssemblyLocation(ServerLevel level, TrackGraphLocation location,
                                                            Vec3 firstBogeyWorld, Vec3 direction,
                                                            List<Vec3> supportPositions) {
        TrackGraph graph = CreateTrackCompatibility.routeGraph(location.graph);
        TrackNode first = graph.locateNode(location.edge.getFirst());
        TrackNode second = graph.locateNode(location.edge.getSecond());
        if (first == null || second == null) throw new IllegalArgumentException("Station edge no longer exists");
        TrackEdge edge = graph.getConnectionsFrom(first).get(second);
        CreateTrackGeometry seed = new CreateTrackGeometry(graph, Objects.requireNonNull(edge, "Station edge"));
        var tangent = seed.sample(Math.max(0, Math.min(1, location.position / edge.getLength()))).tangent();
        double facing = tangent.x() * direction.x + tangent.y() * direction.y + tangent.z() * direction.z;
        if (Math.abs(facing) < .5) throw new IllegalArgumentException("Station route does not follow the assembly direction");
        if (facing < 0) {
            TrackNode swap = first;
            first = second;
            second = swap;
        }
        LinkedList<TrackNode> nodes = new LinkedList<>(List.of(first, second));
        Set<TrackNode> visited = new HashSet<>(nodes);
        double minimum = firstBogeyWorld.dot(direction), maximum = minimum;
        for (Vec3 support : supportPositions) {
            double coordinate = support.dot(direction);
            minimum = Math.min(minimum, coordinate);
            maximum = Math.max(maximum, coordinate);
        }
        RouteChoice assemblyChoice = assemblyContinuation(first.getLocation().getLocation(), direction, minimum, maximum);
        // Preserve the station's assembly direction first. On a loop, extending backwards
        // first can consume a node ahead of the station and truncate the assembly track.
        extend(graph, nodes, visited, true, assemblyChoice);
        extend(graph, nodes, visited, false, assemblyChoice);
        return new CreateRouteBinding(level, graph, nodes, 0, Objects.requireNonNull(firstBogeyWorld));
    }

    private static RouteChoice assemblyContinuation(Vec3 lineOrigin, Vec3 forward, double minimum, double maximum) {
        return (graph, point, front, candidates) -> {
            Vec3 position = point.node2.getLocation().getLocation();
            double coordinate = position.dot(forward);
            if (front ? coordinate >= maximum - 1e-6 : coordinate <= minimum + 1e-6) return null;
            Vec3 offset = position.subtract(lineOrigin);
            if (offset.subtract(forward.scale(offset.dot(forward))).lengthSqr() > 1e-12) return null;
            Vec3 direction = front ? forward : forward.scale(-1);
            Map.Entry<TrackNode, TrackEdge> selected = null;
            for (var candidate : candidates) {
                TrackEdge edge = candidate.getValue();
                // A curved branch may start with the same tangent; it is not the scanned straight track.
                if (edge.isTurn() || edge.isInterDimensional() || edge.getDirection(true).dot(direction) < 1 - 1e-6) continue;
                if (selected != null) return null;
                selected = candidate;
            }
            return selected;
        };
    }

    /** Future train control supplies its chosen node sequence here; loading never chooses a junction. */
    public static CreateRouteBinding selected(ServerLevel level, UUID graphId,
                                               List<TrackNodeLocation> directedNodes, double originDistance) {
        TrackGraph graph = CreateTrackCompatibility.graph(Create.RAILWAYS, graphId);
        if (graph == null) throw new IllegalArgumentException("Saved track graph is unavailable: " + graphId);
        List<TrackNode> nodes = new ArrayList<>(directedNodes.size());
        for (TrackNodeLocation location : directedNodes) {
            TrackNode node = graph.locateNode(location);
            if (node == null) throw new IllegalArgumentException("Selected route node is no longer in the graph");
            nodes.add(node);
        }
        return new CreateRouteBinding(level, graph, nodes, originDistance, null);
    }

    public UUID graphId() { return graph.id; }
    public TrackGraph graph() { return graph; }
    public double originDistance() { return originDistance; }
    public TrackPath path() { return route; }

    /** Continuous steering frame for animation; the solved rail contact and route distances stay authoritative. */
    public TrackGeometry.Sample animationSample(double distance, TrackGeometry.Sample solved) {
        final TrackRoute.Location location;
        try {
            location = route.locate(distance);
        } catch (RouteInvalidException unavailable) {
            // A carrier may still display its last solved pose while the graph is being rebound.
            return solved;
        }
        BoundGeometry segment = segments.get(location.segmentIndex());
        if (segment.surface == null) return solved;
        double parameter = segment.reversed ? 1 - location.parameter() : location.parameter();
        var frame = segment.geometry.animationSample(parameter);
        return new TrackGeometry.Sample(solved.position(),
                segment.reversed ? frame.tangent().scale(-1) : frame.tangent(), frame.normal(),
                segment.reversed ? -frame.cantRadians() : frame.cantRadians());
    }

    public TravellingPoint pointAt(double distance) { return pointAt(distance, true); }

    /**
     * Create's two compatibility wheel points extend beyond our actual bogey support by half
     * the registered wheel spacing. At a physical track end that virtual point can leave the
     * route while the support is still valid. Keep its native reference on the terminal node;
     * the independent support distance and body placement are not changed.
     */
    public TravellingPoint boundedPointAt(double distance) { return pointAt(boundDistance(distance)); }

    TravellingPoint boundedPointAt(double distance, boolean forward) { return pointAt(boundDistance(distance), forward); }

    private double boundDistance(double distance) {
        if (!Double.isFinite(distance)) throw new IllegalArgumentException("Distance must be finite");
        return Math.clamp(distance, route.minimumDistance(), route.maximumDistance());
    }

    /** Create stores parameter * declared edge length; our runtime stores measured arc length. */
    public TravellingPoint pointAt(double distance, boolean forward) {
        TrackRoute.Location location = route.locate(distance);
        BoundGeometry segment = segments.get(location.segmentIndex());
        TrackNode from = forward ? segment.from : segment.to;
        TrackNode to = forward ? segment.to : segment.from;
        TrackEdge edge = graph.getConnectionsFrom(from).get(to);
        if (edge == null || !edgePositions.containsKey(edge)) throw new RouteInvalidException();
        return CreateTrackCompatibility.point(graph, from, to, edge,
                (forward ? location.parameter() : 1 - location.parameter()) * edge.getLength(), false);
    }

    /** Exact inverse on this selected route; an unselected or stale edge is rejected. */
    public double distanceOf(TravellingPoint point) {
        Objects.requireNonNull(point);
        EdgePosition selected = edgePositions.get(point.edge);
        if (selected == null)
            throw new IllegalArgumentException("Point is not on the selected route");
        point = CreateTrackCompatibility.onGraph(graph, point);
        BoundGeometry segment = segments.get(selected.segment);
        if (!segment.valid()) throw new RouteInvalidException();
        double parameter = parameterOf(point);
        return route.distanceAt(selected.segment, selected.reverse ? 1 - parameter : parameter);
    }

    private static double parameterOf(TravellingPoint point) {
        double parameter = point.position / point.edge.getLength();
        if (!Double.isFinite(parameter) || parameter < 0 || parameter > 1)
            throw new IllegalArgumentException("Point is outside its track edge");
        return parameter;
    }

    /** Measured arc length on bound track; Create's edge distance on an unbound preview branch. */
    double previewDistanceOn(TrackEdge edge, double position) {
        var known = edgePositions.get(edge);
        if (known == null || edge.getLength() == 0) return position;
        double t = Math.clamp(position / edge.getLength(), 0, 1);
        return Math.abs(route.distanceAt(known.segment, known.reverse ? 1 - t : t)
                - route.distanceAt(known.segment, known.reverse ? 1 : 0));
    }

    TrackNode boundExitAfter(TrackEdge incoming) {
        var pos = edgePositions.get(incoming);
        if (pos == null) return null;
        int next = pos.reverse ? pos.segment - 1 : pos.segment + 2;
        return next < 0 || next >= nodes.size() ? null : nodes.get(next);
    }

    /**
     * Notify Create's existing station/signal listeners along the actual solved arc interval.
     * This does not move a Create carriage or choose an exit. Like TravellingPoint, the start
     * boundary is included and the destination boundary is excluded until the next movement.
     * Compatibility wheel points may overhang a finite route. Only its existing edges are
     * visited, but event inclusion and traveled distance use the original interval so crossing
     * a terminal node still fires its event. With no stop, the original destination is returned.
     */
    public double traversePoints(double from, double to, TravellingPoint.IEdgePointListener listener) {
        return traversePoints(from, to, listener, null);
    }

    /** At a crossed fork, notify control of the already selected exit without choosing a new route. */
    public double traversePoints(double from, double to, TravellingPoint.IEdgePointListener listener,
                                 BiConsumer<TravellingPoint, List<Map.Entry<TrackNode, TrackEdge>>> crossedJunction) {
        Objects.requireNonNull(listener);
        var first = route.locate(boundDistance(from));
        var last = route.locate(boundDistance(to));
        if (from == to) return to;
        boolean forward = to > from;
        int step = forward ? 1 : -1;
        int start = first.segmentIndex();
        // locate uses the next edge at an exact node. Retain a point stored on the preceding
        // edge's endpoint when the previous tick stopped exactly there without crossing it.
        if (forward && start > 0 && first.parameter() == 0) start--;
        for (int index = start; ; index += step) {
            BoundGeometry segment = segments.get(index);
            if (!segment.valid()) throw new RouteInvalidException();
            var points = segment.edge.getEdgeData().getPoints();
            for (int j = 0; j < points.size(); j++) {
                var point = points.get(forward ? j : points.size() - j - 1);
                double parameter = point.getLocationOn(segment.edge) / segment.edge.getLength();
                double location = route.distanceAt(index, parameter);
                if (forward ? location < from || location >= to : location > from || location <= to) continue;
                var nodes = forward ? Couple.create(segment.from, segment.to) : Couple.create(segment.to, segment.from);
                if (listener.test(Math.abs(location - from), Pair.of(point, nodes))) return location;
            }
            if (index == last.segmentIndex()) return to;
            if (crossedJunction != null) {
                BoundGeometry next = segments.get(index + step);
                if (!next.valid()) throw new RouteInvalidException();
                TrackNode previousNode = forward ? segment.from : segment.to;
                TrackNode currentNode = forward ? segment.to : segment.from;
                TrackNode nextNode = forward ? next.to : next.from;
                TrackEdge incoming = graph.getConnectionsFrom(previousNode).get(currentNode);
                TrackEdge outgoing = graph.getConnectionsFrom(currentNode).get(nextNode);
                if (incoming == null || outgoing == null) throw new RouteInvalidException();
                int exits = 0;
                for (var candidate : graph.getConnectionsFrom(currentNode).entrySet())
                    if (candidate.getKey() != previousNode && incoming.canTravelTo(candidate.getValue())) exits++;
                if (exits > 1)
                    crossedJunction.accept(CreateTrackCompatibility.point(graph, previousNode, currentNode, incoming,
                            incoming.getLength(), false), List.of(Map.entry(nextNode, outgoing)));
            }
        }
    }

    /** Defensive copies: Create's TrackNodeLocation has mutable dimension/height fields. */
    public List<TrackNodeLocation> nodes() {
        return nodes.stream().map(node -> TrackNodeLocation.read(node.getLocation().write(null), null).in(level)).toList();
    }

    /** Full validation for assembly/resume only. Sampling validates just the sampled edge. */
    public boolean isValid() {
        for (BoundGeometry segment : segments) if (!segment.valid()) return false;
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", geometryVersion);
        tag.putUUID("Graph", segments.getFirst().owner.id);
        ListTag graphs = new ListTag();
        for (var id : segments.stream().map(segment -> segment.owner.id).distinct().toList()) {
            var entry = new CompoundTag(); entry.putUUID("Id", id); graphs.add(entry);
        }
        tag.put("Graphs", graphs);
        tag.putString("Dimension", level.dimension().location().toString());
        tag.putDouble("Origin", originDistance);
        ListTag savedNodes = new ListTag();
        for (TrackNode node : nodes) savedNodes.add(node.getLocation().write(null));
        tag.put("Nodes", savedNodes);
        return tag;
    }

    public static CreateRouteBinding load(ServerLevel level, CompoundTag tag) {
        if (tag.getInt("Version") < 1 || tag.getInt("Version") > GEOMETRY_VERSION || !tag.hasUUID("Graph") || !tag.contains("Origin", Tag.TAG_DOUBLE)
                || !level.dimension().location().toString().equals(tag.getString("Dimension")))
            throw new IllegalArgumentException("Invalid saved route binding");
        ListTag saved = tag.getList("Nodes", Tag.TAG_COMPOUND);
        if (saved.size() < 2 || saved.size() > MAX_EDGES + 1)
            throw new IllegalArgumentException("Invalid saved route size");
        List<TrackNodeLocation> nodes = new ArrayList<>(saved.size());
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag node = saved.getCompound(i);
            if (!node.contains("Pos")) throw new IllegalArgumentException("Missing saved node coordinates");
            nodes.add(TrackNodeLocation.read(node, null).in(level));
        }
        var savedGraphs = tag.getList("Graphs", Tag.TAG_COMPOUND);
        var graphIds = new ArrayList<UUID>();
        for (var item : savedGraphs) {
            var entry = (CompoundTag) item;
            if (!entry.hasUUID("Id")) throw new IllegalArgumentException("Invalid saved graph identity");
            graphIds.add(entry.getUUID("Id"));
        }
        if (graphIds.isEmpty()) graphIds.add(tag.getUUID("Graph"));
        var graph = CreateTrackCompatibility.resolveGraphs(Create.RAILWAYS, graphIds);
        if (graph == null) throw new IllegalArgumentException("Saved track graph is unavailable");
        graph = CreateTrackCompatibility.routeGraph(graph);
        var resolved = new ArrayList<TrackNode>(nodes.size());
        for (var location : nodes) {
            var node = graph.locateNode(location);
            if (node == null) throw new IllegalArgumentException("Saved route node is no longer in the graph");
            resolved.add(node);
        }
        return new CreateRouteBinding(level, graph, resolved, tag.getDouble("Origin"), null, tag.getInt("Version"));
    }

    /** Assembly-only nearest projection; returns geometric arc distance, never a Bezier parameter. */
    public double project(Vec3 worldPosition) {
        if (!Double.isFinite(worldPosition.x) || !Double.isFinite(worldPosition.y) || !Double.isFinite(worldPosition.z))
            throw new IllegalArgumentException("Projection position must be finite");
        double bestError = Double.POSITIVE_INFINITY;
        int bestSegment = 0;
        double bestParameter = 0;
        // Create curves are cubic. Bracket their nearest coarse sample, then refine on that segment.
        for (int i = 0; i < segments.size(); i++) {
            BoundGeometry segment = segments.get(i);
            int nearest = 0;
            double error = Double.POSITIVE_INFINITY;
            for (int j = 0; j <= 64; j++) {
                double value = squaredDistance(segment.directedSample(j / 64.0).position(), worldPosition);
                if (value < error) { error = value; nearest = j; }
            }
            double lower = Math.max(0, (nearest - 1) / 64.0);
            double upper = Math.min(1, (nearest + 1) / 64.0);
            for (int j = 0; j < 48; j++) {
                double a = lower + (upper - lower) / 3;
                double b = upper - (upper - lower) / 3;
                if (squaredDistance(segment.directedSample(a).position(), worldPosition)
                        <= squaredDistance(segment.directedSample(b).position(), worldPosition)) upper = b;
                else lower = a;
            }
            double parameter = (lower + upper) * .5;
            double refined = squaredDistance(segment.directedSample(parameter).position(), worldPosition);
            if (refined < error) error = refined;
            else parameter = nearest / 64.0;
            if (error < bestError) { bestError = error; bestSegment = i; bestParameter = parameter; }
        }
        return route.distanceAt(bestSegment, bestParameter);
    }

    private static double squaredDistance(TrackGeometry.Vector position, Vec3 other) {
        double x = position.x() - other.x, y = position.y() - other.y, z = position.z() - other.z;
        return x * x + y * y + z * z;
    }

    private static void extend(TrackGraph graph, LinkedList<TrackNode> nodes, Set<TrackNode> visited, boolean front) {
        extend(graph, nodes, visited, front, null);
    }

    private static void extend(TrackGraph graph, LinkedList<TrackNode> nodes, Set<TrackNode> visited,
                               boolean front, RouteChoice choice) {
        while (true) {
            TrackNode current = front ? nodes.getLast() : nodes.getFirst();
            TrackNode previous = front ? nodes.get(nodes.size() - 2) : nodes.get(1);
            TrackEdge incoming = graph.getConnectionsFrom(previous).get(current);
            if (incoming == null) throw new IllegalArgumentException("Route needs both directed track connections");
            var candidates = continuations(graph, previous, current, incoming);
            if (candidates.isEmpty()) return;
            Map.Entry<TrackNode, TrackEdge> selected;
            if (candidates.size() == 1) selected = candidates.getFirst();
            else {
                if (choice == null) return;
                selected = choice.choose(graph, CreateTrackCompatibility.point(graph, previous, current, incoming,
                        incoming.getLength(), false), front, List.copyOf(candidates));
                if (selected == null) return;
                if (candidates.stream().noneMatch(candidate -> candidate.getKey() == selected.getKey()
                        && candidate.getValue() == selected.getValue()))
                    throw new IllegalArgumentException("Route choice returned an unavailable exit");
            }
            TrackNode next = selected.getKey();
            TrackEdge continuation = selected.getValue();
            if (next == null || visited.contains(next) || !isOwnTrack(continuation)
                    || continuation.isInterDimensional()) return;
            if (nodes.size() > MAX_EDGES) throw new IllegalArgumentException("Station route exceeds " + MAX_EDGES + " edges");
            visited.add(next);
            if (front) nodes.addLast(next); else nodes.addFirst(next);
        }
    }

    static List<Map.Entry<TrackNode, TrackEdge>> continuations(TrackGraph graph, TrackNode previous, TrackNode current, TrackEdge incoming) {
        var candidates = new ArrayList<Map.Entry<TrackNode, TrackEdge>>();
        for (var exit : graph.getConnectionsFrom(current).entrySet()) {
            if (exit.getKey() != previous && incoming.canTravelTo(exit.getValue()))
                candidates.add(Map.entry(CreateTrackCompatibility.canonical(graph, exit.getKey()), exit.getValue()));
        }
        return candidates;
    }

    /** The probe faces outward; manual steering is expressed along the train's positive axis. */
    static Map.Entry<TrackNode, TrackEdge> manualExit(TrackGraph graph, TravellingPoint probe, boolean forward,
            TravellingPoint.SteerDirection steer, List<Map.Entry<TrackNode, TrackEdge>> choices) {
        if (!forward) steer = switch (steer) {
            case LEFT -> TravellingPoint.SteerDirection.RIGHT;
            case RIGHT -> TravellingPoint.SteerDirection.LEFT;
            case NONE -> TravellingPoint.SteerDirection.NONE;
        };
        return probe.steer(steer, probe.edge.getNormal(graph, .5)).apply(graph, Pair.of(true, choices));
    }

    static boolean isOwnTrack(TrackEdge edge) {
        var material = edge.getTrackMaterial();
        return CreateRailwayBackend.supports(material);
    }

    private static final class BoundGeometry implements TrackGeometry {
        private final TrackGraph graph;
        private final TrackGraph owner;
        private final TrackNode from;
        private final TrackNode to;
        private final TrackEdge edge;
        private final TrackEdge canonicalEdge;
        private final boolean reversed;
        private final CreateTrackGeometry geometry;
        private final TrackRailSurface surface;
        private final TrackRailSurface.Section straightFrom, straightTo;
        private final Map<TrackNode, TrackEdge> fromSmoothingEdges;
        private final Map<TrackNode, TrackEdge> toSmoothingEdges;

        private BoundGeometry(TrackGraph graph, TrackNode from, TrackNode to, int geometryVersion) {
            this.graph = graph;
            this.from = from;
            this.to = to;
            edge = graph.getConnectionsFrom(from).get(to);
            if (edge == null) throw new IllegalArgumentException("Selected directed edge is unavailable");
            owner = CreateTrackCompatibility.physicalGraph(graph, edge);
            TrackEdge opposite = graph.getConnectionsFrom(to).get(from);
            if (opposite == null) throw new IllegalArgumentException("Selected track needs a reverse edge");
            // Create renders primary curves. secondary() recomputes handles and can produce
            // a different curve, so both travel directions must measure that same primary.
            boolean useOpposite = geometryVersion >= 2 && (edge.isTurn() && opposite.isTurn()
                    && edge.getTurn().primary != opposite.getTurn().primary
                    ? opposite.getTurn().primary : orderedAfter(from, to));
            canonicalEdge = useOpposite ? opposite : edge;
            reversed = useOpposite;
            geometry = new CreateTrackGeometry(graph, canonicalEdge, geometryVersion >= 3, geometryVersion >= 4);
            surface = geometryVersion >= 5 && canonicalEdge.isTurn() ? new TrackRailSurface(canonicalEdge.getTurn()) : null;
            if (geometryVersion >= 5 && !canonicalEdge.isTurn()) {
                var joints = geometry.straightSections();
                straightFrom = joints.getFirst();
                straightTo = joints.getSecond();
            } else {
                straightFrom = straightTo = null;
            }
            boolean smoothed = !edge.isTurn()
                    && (from.getLocation().yOffsetPixels != 0 || to.getLocation().yOffsetPixels != 0
                    || geometryVersion >= 5 || geometryVersion >= 4 && CreateTrackCompatibility.straightUsesAdjacentCurves());
            fromSmoothingEdges = smoothed ? Map.copyOf(graph.getConnectionsFrom(from)) : null;
            toSmoothingEdges = smoothed ? Map.copyOf(graph.getConnectionsFrom(to)) : null;
        }

        private boolean valid() {
            return Create.RAILWAYS.trackNetworks.get(owner.id) == owner
                    && CreateTrackCompatibility.containsDirectedEdge(owner, edge)
                    && graph.getConnectionsFrom(from).get(to) == edge
                    && (!reversed || graph.getConnectionsFrom(to).get(from) == canonicalEdge)
                    // Raised Create straights and RailX straights depend on adjacent curve endpoints.
                    && (fromSmoothingEdges == null || (fromSmoothingEdges.equals(graph.getConnectionsFrom(from))
                    && toSmoothingEdges.equals(graph.getConnectionsFrom(to))));
        }

        @Override public double length() { return geometry.length(); }

        @Override public boolean allowsBogey(String modelId) {
            return willow.train.hexie.api.railway.BogeyRailwayRegistry.allows(modelId, edge.getTrackMaterial().id);
        }

        private static boolean orderedAfter(TrackNode first, TrackNode second) {
            var a = first.getLocation().getLocation();
            var b = second.getLocation().getLocation();
            int order = Double.compare(a.x, b.x);
            if (order == 0) order = Double.compare(a.y, b.y);
            if (order == 0) order = Double.compare(a.z, b.z);
            return order > 0;
        }

        private Sample directedSample(double parameter) {
            return sample(reversed ? 1 - parameter : parameter);
        }

        @Override public int linearSpanCount() { return surface == null ? 0 : surface.count(); }
        @Override public double linearSpanEnd(int index) { return surface.parameter(index + 1); }

        @Override public Sample sample(double parameter) {
            if (!valid()) throw new RouteInvalidException();
            if (surface != null) return surface.contact(parameter);
            if (straightFrom != null) {
                Vec3 tangent = straightTo.center().subtract(straightFrom.center());
                Vec3 normal = straightFrom.heightAxis().lerp(straightTo.heightAxis(), parameter);
                return TrackRailSurface.contact(straightFrom.center().lerp(straightTo.center(), parameter), tangent, normal);
            }
            Sample sample = geometry.sample(parameter);
            var p = sample.position();
            // Versions 1-4 reproduce the saved frames before migrating.
            return Sample.restoreNormalized(new Vector(p.x(), p.y() + TrackMaterialInit.RAIL_SURFACE_HEIGHT, p.z()),
                    sample.tangent(), sample.normal(), sample.cantRadians());
        }
    }
}
