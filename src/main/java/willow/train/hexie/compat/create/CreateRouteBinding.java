package willow.train.hexie.compat.create;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.vehicle.track.TrackGeometry;
import willow.train.hexie.vehicle.track.TrackPath;
import willow.train.hexie.vehicle.track.TrackRoute;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** A saved, explicitly directed Create route. All access is on the server's owning thread. */
public final class CreateRouteBinding {
    public static final class RouteInvalidException extends IllegalStateException {
        public RouteInvalidException() { super("Selected track edge changed; route must be rebound"); }
    }

    private static final int MAX_EDGES = 4096;
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
        this(level, graph, nodes, originDistance, initialPosition, 3);
    }

    private CreateRouteBinding(ServerLevel level, TrackGraph graph, List<TrackNode> nodes,
                               double originDistance, Vec3 initialPosition, int geometryVersion) {
        this.level = Objects.requireNonNull(level);
        this.graph = Objects.requireNonNull(graph);
        this.nodes = List.copyOf(nodes);
        this.geometryVersion = geometryVersion;
        if (nodes.size() < 2 || nodes.size() > MAX_EDGES + 1)
            throw new IllegalArgumentException("Route must contain between 1 and " + MAX_EDGES + " edges");
        List<BoundGeometry> geometry = new ArrayList<>(nodes.size() - 1);
        for (TrackNode node : nodes) {
            if (!level.dimension().equals(node.getLocation().getDimension())
                    || graph.locateNode(node.getLocation()) != node)
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
        Objects.requireNonNull(anchor);
        if (anchor.node1 == null || anchor.node2 == null || anchor.edge == null
                || graph.getConnectionsFrom(anchor.node1).get(anchor.node2) != anchor.edge)
            throw new IllegalArgumentException("Anchor point is not on this graph");
        parameterOf(anchor);
        LinkedList<TrackNode> nodes = new LinkedList<>(forward
                ? List.of(anchor.node1, anchor.node2) : List.of(anchor.node2, anchor.node1));
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
        try {
            var selected = new CreateRouteBinding(level, graph, List.of(anchor.node1, anchor.node2), 0, null);
            selected.distanceOf(anchor);
            for (var point : occupied) {
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

    /** Refresh only sampled/next-tick edges, not distant history or future selected track. */
    public Extension refresh(double minimumNeeded, double maximumNeeded) {
        int first = route.segmentIndex(boundDistance(minimumNeeded));
        int end = route.segmentIndex(boundDistance(maximumNeeded)) + 1;
        boolean current = true;
        for (int i = first; i < end; i++) current &= segments.get(i).valid();
        if (current) return new Extension(this, 0);
        var currentGraph = Create.RAILWAYS.trackNetworks.get(graph.id);
        if (currentGraph == null) throw new RouteInvalidException();
        var resolved = new ArrayList<TrackNode>(end - first + 1);
        for (int i = first; i <= end; i++) {
            var node = currentGraph.locateNode(nodes.get(i).getLocation());
            if (node == null) throw new RouteInvalidException();
            if (!resolved.isEmpty()) {
                var previous = resolved.getLast();
                if (currentGraph.getConnectionsFrom(previous).get(node) == null
                        || currentGraph.getConnectionsFrom(node).get(previous) == null)
                    throw new RouteInvalidException();
            }
            resolved.add(node);
        }
        double shift = -route.distanceAt(first, 0);
        var refreshed = new CreateRouteBinding(level, currentGraph, resolved, 0, null, geometryVersion);
        return new Extension(refreshed, shift);
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
        if (level != other.level || graph != other.graph)
            throw new IllegalArgumentException("Coupling routes must belong to the same level and track graph");
        if (!isValid() || !other.isValid()) throw new RouteInvalidException();
        if (other == this) return new Merge(this, 0, 0, false);

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
                    if (next.getKey() == current.previous || left.contains(next.getKey())
                            || next.getKey() != target && right.contains(next.getKey())) continue;
                    TrackEdge edge = next.getValue();
                    if (!isOwnTrack(edge) || edge.isInterDimensional() || !incoming.canTravelTo(edge)) continue;
                    if (graph.getConnectionsFrom(next.getKey()).get(current.node) == null) continue;
                    queue.add(new SearchStep(current.node, next.getKey(), current.distance + edge.getLength(), current));
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

    /** Upgrade old edge direction/tangent sampling through each support's exact edge parameter. */
    CreateRouteBinding canonicalized() {
        if (geometryVersion >= 3) return this;
        var upgraded = new CreateRouteBinding(level, graph, nodes, 0, null, 3);
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
        TrackGraph graph = location.graph;
        TrackNode first = graph.locateNode(location.edge.getFirst());
        TrackNode second = graph.locateNode(location.edge.getSecond());
        if (first == null || second == null) throw new IllegalArgumentException("Station edge no longer exists");
        TrackEdge edge = graph.getConnectionsFrom(first).get(second);
        CreateTrackGeometry seed = new CreateTrackGeometry(graph, Objects.requireNonNull(edge, "Station edge"));
        Vec3 direction = Vec3.atLowerCornerOf(forward.getNormal());
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
        for (BlockPos support : supportPositions) {
            double coordinate = Vec3.atCenterOf(support).dot(direction);
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
        TrackGraph graph = Create.RAILWAYS.trackNetworks.get(graphId);
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

    public TravellingPoint pointAt(double distance) { return pointAt(distance, true); }

    /**
     * Create's two compatibility wheel points extend beyond our actual bogey support by half
     * the registered wheel spacing. At a physical track end that virtual point can leave the
     * route while the support is still valid. Keep its native reference on the terminal node;
     * the independent support distance and body placement are not changed.
     */
    public TravellingPoint boundedPointAt(double distance) { return pointAt(boundDistance(distance)); }

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
        return new TravellingPoint(from, to, edge,
                (forward ? location.parameter() : 1 - location.parameter()) * edge.getLength(), false);
    }

    /** Exact inverse on this selected route; an unselected or stale edge is rejected. */
    public double distanceOf(TravellingPoint point) {
        Objects.requireNonNull(point);
        EdgePosition selected = edgePositions.get(point.edge);
        if (selected == null || graph.getConnectionsFrom(point.node1).get(point.node2) != point.edge)
            throw new IllegalArgumentException("Point is not on the selected route");
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
                    crossedJunction.accept(new TravellingPoint(previousNode, currentNode, incoming,
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
        tag.putUUID("Graph", graph.id);
        tag.putString("Dimension", level.dimension().location().toString());
        tag.putDouble("Origin", originDistance);
        ListTag savedNodes = new ListTag();
        for (TrackNode node : nodes) savedNodes.add(node.getLocation().write(null));
        tag.put("Nodes", savedNodes);
        return tag;
    }

    public static CreateRouteBinding load(ServerLevel level, CompoundTag tag) {
        if (tag.getInt("Version") < 1 || tag.getInt("Version") > 3 || !tag.hasUUID("Graph") || !tag.contains("Origin", Tag.TAG_DOUBLE)
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
        var graph = Create.RAILWAYS.trackNetworks.get(tag.getUUID("Graph"));
        if (graph == null) throw new IllegalArgumentException("Saved track graph is unavailable");
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
            List<Map.Entry<TrackNode, TrackEdge>> candidates = new ArrayList<>();
            for (var candidate : graph.getConnectionsFrom(current).entrySet()) {
                if (candidate.getKey() == previous || !incoming.canTravelTo(candidate.getValue())) continue;
                // Count foreign track too: material filtering must not silently select a junction exit.
                candidates.add(Map.entry(candidate.getKey(), candidate.getValue()));
            }
            if (candidates.isEmpty()) return;
            Map.Entry<TrackNode, TrackEdge> selected;
            if (candidates.size() == 1) selected = candidates.getFirst();
            else {
                if (choice == null) return;
                selected = choice.choose(graph, new TravellingPoint(previous, current, incoming,
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

    private static boolean isOwnTrack(TrackEdge edge) {
        var material = edge.getTrackMaterial();
        return material == TrackMaterialInit.STANDARD || material == TrackMaterialInit.BALLASTLESS
                || material == TrackMaterialInit.TIELESS;
    }

    private static final class BoundGeometry implements TrackGeometry {
        private final TrackGraph graph;
        private final TrackNode from;
        private final TrackNode to;
        private final TrackEdge edge;
        private final TrackEdge canonicalEdge;
        private final boolean reversed;
        private final CreateTrackGeometry geometry;
        private final Map<TrackNode, TrackEdge> fromSmoothingEdges;
        private final Map<TrackNode, TrackEdge> toSmoothingEdges;

        private BoundGeometry(TrackGraph graph, TrackNode from, TrackNode to, int geometryVersion) {
            this.graph = graph;
            this.from = from;
            this.to = to;
            edge = graph.getConnectionsFrom(from).get(to);
            if (edge == null) throw new IllegalArgumentException("Selected directed edge is unavailable");
            TrackEdge opposite = graph.getConnectionsFrom(to).get(from);
            if (opposite == null) throw new IllegalArgumentException("Selected track needs a reverse edge");
            // Create renders primary curves. secondary() recomputes handles and can produce
            // a different curve, so both travel directions must measure that same primary.
            boolean useOpposite = geometryVersion >= 2 && (edge.isTurn() && opposite.isTurn()
                    && edge.getTurn().primary != opposite.getTurn().primary
                    ? opposite.getTurn().primary : orderedAfter(from, to));
            canonicalEdge = useOpposite ? opposite : edge;
            reversed = useOpposite;
            geometry = new CreateTrackGeometry(graph, canonicalEdge, geometryVersion >= 3);
            boolean smoothed = !edge.isTurn()
                    && (from.getLocation().yOffsetPixels != 0 || to.getLocation().yOffsetPixels != 0);
            fromSmoothingEdges = smoothed ? Map.copyOf(graph.getConnectionsFrom(from)) : null;
            toSmoothingEdges = smoothed ? Map.copyOf(graph.getConnectionsFrom(to)) : null;
        }

        private boolean valid() {
            return Create.RAILWAYS.trackNetworks.get(graph.id) == graph
                    && graph.locateNode(from.getLocation()) == from && graph.locateNode(to.getLocation()) == to
                    && graph.getConnectionsFrom(from).get(to) == edge
                    && (!reversed || graph.getConnectionsFrom(to).get(from) == canonicalEdge)
                    // Create's raised straight edges also derive endpoints/normals from adjacent curves.
                    && (fromSmoothingEdges == null || (fromSmoothingEdges.equals(graph.getConnectionsFrom(from))
                    && toSmoothingEdges.equals(graph.getConnectionsFrom(to))));
        }

        @Override public double length() { return geometry.length(); }

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

        @Override public Sample sample(double parameter) {
            if (!valid()) throw new RouteInvalidException();
            Sample sample = geometry.sample(parameter);
            var p = sample.position();
            // Our current track models/overlays are raised vertically, not along a cant normal.
            return Sample.restoreNormalized(new Vector(p.x(), p.y() + TrackMaterialInit.RAIL_SURFACE_HEIGHT, p.z()),
                    sample.tangent(), sample.normal(), sample.cantRadians());
        }
    }
}
