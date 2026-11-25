package willow.train.hexie.compat.create.signal;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
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
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.railway.RailwayBridge.TrackRouteLocationView;
import willow.train.hexie.bridge.railway.TrackGeometryView.Sample;
import willow.train.hexie.bridge.railway.TrackPathView;
import willow.train.hexie.bridge.railway.TrackRouteView;
import willow.train.hexie.bridge.vehicle.VehicleBridge.VehicleAssemblyPreparerPreparedView;
import willow.train.hexie.compat.create.assembly.CreateAssemblyRouteBinding;
import willow.train.hexie.compat.create.assembly.CreateImportedCarriageRoute;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.track.CreateRailwayBackend;
import willow.train.hexie.compat.create.track.CreateRouteGeometry;
import willow.train.hexie.compat.create.track.CreateTrackCompatibility;

/** A saved, explicitly directed Create route. All access is on the server's owning thread. */
public final class CreateRouteBinding {
    private final CreateRouteMerge routeMerge = new CreateRouteMerge(this);
    private final CreateRoutePersistence persistence = new CreateRoutePersistence(this);
    private final CreateRouteGeometry geometry = new CreateRouteGeometry(this);

    public static CreateRouteBinding fromStation(StationBlockEntity station, Vec3 firstBogeyWorld,
                                                 Direction forward) {
        return CreateAssemblyRouteBinding.fromStation(station, firstBogeyWorld, forward);
    }

    public static CreateRouteBinding fromStation(StationBlockEntity station, Vec3 firstBogeyWorld,
                                                 Direction forward, List<BlockPos> supportPositions) {
        return CreateAssemblyRouteBinding.fromStation(station, firstBogeyWorld, forward, supportPositions);
    }

    static CreateRouteBinding forImportedCarriage(SnapshotCarriageEntity carrier,
                                                 VehicleAssemblyPreparerPreparedView prepared) {
        return CreateImportedCarriageRoute.forImportedCarriage(carrier, prepared);
    }

    public CreateRouteMerge.Merge connectForCoupling(CreateRouteBinding other, double firstMin, double firstMax,
            double secondMin, double secondMax) {
        return routeMerge.connectForCoupling(other, firstMin, firstMax, secondMin, secondMax);
    }

    public Extension occupied(double min, double max) {
        return routeMerge.occupied(min, max);
    }

    public CreateRouteMerge.Merge merge(CreateRouteBinding other) {
        return routeMerge.merge(other);
    }




    public CreateRouteMerge.Merge merge(CreateRouteBinding other, CreateRouteMerge.MergePlan plan) {
        return routeMerge.merge(other, plan);
    }

    public CompoundTag save() {
        return persistence.save();
    }

    public static CreateRouteBinding load(ServerLevel level, CompoundTag tag) {
        return CreateRoutePersistence.load(level, tag);
    }

    public Sample animationSample(double distance, Sample solved) {
        return geometry.animationSample(distance, solved);
    }

    public double project(Vec3 worldPosition) {
        return geometry.project(worldPosition);
    }

    public static final class RouteInvalidException extends IllegalStateException {
        public RouteInvalidException() { super("Selected track edge changed; route must be rebound"); }
    }

    static final int MAX_EDGES = 4096;
    static final int GEOMETRY_VERSION = 5;

    final ServerLevel level;
    final TrackGraph graph;
    public final List<TrackNode> nodes;
    public final List<CreateRouteGeometry.BoundGeometry> segments;
    public final TrackRouteView route;
    final double originDistance;
    final int geometryVersion;
    final Map<TrackEdge, EdgePosition> edgePositions = new IdentityHashMap<>();

    /** Preview is at its node2, facing outward. Choosing never advances a live Create point. */
    @FunctionalInterface
    public interface RouteChoice {
        Map.Entry<TrackNode, TrackEdge> choose(TrackGraph graph, TravellingPoint preview,
                                             boolean extendingForward,
                                             List<Map.Entry<TrackNode, TrackEdge>> candidates);
    }

    /** Add distanceOffset to every old route cursor when replacing a binding with this result. */
    public record Extension(CreateRouteBinding binding, double distanceOffset) {}

    record EdgePosition(int segment, boolean reverse) {}

    public CreateRouteBinding(ServerLevel level, TrackGraph graph, List<TrackNode> nodes,
                               double originDistance, Vec3 initialPosition) {
        this(level, graph, nodes, originDistance, initialPosition, GEOMETRY_VERSION);
    }

    public CreateRouteBinding(ServerLevel level, TrackGraph graph, List<TrackNode> nodes,
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
        List<CreateRouteGeometry.BoundGeometry> geometry = new ArrayList<>(nodes.size() - 1);
        for (TrackNode node : nodes) {
            if (!level.dimension().equals(node.getLocation().getDimension())
                    || !CreateTrackCompatibility.owns(graph, node))
                throw new IllegalArgumentException("Route node is not in this level and graph");
        }
        for (int i = 1; i < nodes.size(); i++)
            geometry.add(new CreateRouteGeometry.BoundGeometry(graph, nodes.get(i - 1), nodes.get(i), geometryVersion));
        segments = List.copyOf(geometry);
        route = HexieBridges.railway().trackRoute().directed(CreateRouteMerge.mapped(segments));
        indexEdges();
        this.originDistance = initialPosition == null ? originDistance : project(initialPosition);
        if (!Double.isFinite(this.originDistance) || this.originDistance < 0 || this.originDistance > route.length())
            throw new IllegalArgumentException("Origin is outside the selected route");
    }

    public CreateRouteBinding(CreateRouteBinding previous, List<TrackNode> nodes,
                               List<CreateRouteGeometry.BoundGeometry> segments, TrackRouteView route, double offset) {
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
            CreateRouteGeometry.BoundGeometry segment = segments.get(i);
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

    public static CreateRouteBinding fromPoints(ServerLevel level, TrackGraph graph, TravellingPoint anchor,
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
        List<CreateRouteGeometry.BoundGeometry> extra = new ArrayList<>(added);
        int first = forward ? nodes.size() - 1 : 0;
        for (int i = first; i < first + added; i++)
            extra.add(new CreateRouteGeometry.BoundGeometry(graph, extendedNodes.get(i), extendedNodes.get(i + 1), geometryVersion));
        TrackRouteView extended = route.extendedDirected(forward ? List.of() : CreateRouteMerge.mapped(extra), forward ? CreateRouteMerge.mapped(extra) : List.of());
        double offset = forward ? 0 : extended.distanceAt(added, 0);
        List<CreateRouteGeometry.BoundGeometry> geometry = new ArrayList<>(extendedNodes.size() - 1);
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
    public Extension reselectAhead(boolean forward, RouteChoice choice, double distance, double lookAhead,
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

    /** Upgrade old geometry through each support's exact edge parameter, preserving saved frames until remap. */
    public CreateRouteBinding canonicalized() {
        if (geometryVersion >= GEOMETRY_VERSION) return this;
        var upgraded = new CreateRouteBinding(level, graph, nodes, 0, null, GEOMETRY_VERSION);
        return new CreateRouteBinding(upgraded, nodes, upgraded.segments, upgraded.route,
                upgraded.distanceOf(pointAt(originDistance)));
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
    public TrackPathView path() { return route; }

    public TravellingPoint pointAt(double distance) { return pointAt(distance, true); }

    /**
     * Create's two compatibility wheel points extend beyond our actual bogey support by half
     * the registered wheel spacing. At a physical track end that virtual point can leave the
     * route while the support is still valid. Keep its native reference on the terminal node;
     * the independent support distance and body placement are not changed.
     */
    public TravellingPoint boundedPointAt(double distance) { return pointAt(boundDistance(distance)); }

    public TravellingPoint boundedPointAt(double distance, boolean forward) { return pointAt(boundDistance(distance), forward); }

    double boundDistance(double distance) {
        if (!Double.isFinite(distance)) throw new IllegalArgumentException("Distance must be finite");
        return Math.clamp(distance, route.minimumDistance(), route.maximumDistance());
    }

    /** Create stores parameter * declared edge length; our runtime stores measured arc length. */
    public TravellingPoint pointAt(double distance, boolean forward) {
        TrackRouteLocationView location = route.locate(distance);
        CreateRouteGeometry.BoundGeometry segment = segments.get(location.segmentIndex());
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
        CreateRouteGeometry.BoundGeometry segment = segments.get(selected.segment);
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
            CreateRouteGeometry.BoundGeometry segment = segments.get(index);
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
                CreateRouteGeometry.BoundGeometry next = segments.get(index + step);
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
        for (CreateRouteGeometry.BoundGeometry segment : segments) if (!segment.valid()) return false;
        return true;
    }

    public static void extend(TrackGraph graph, LinkedList<TrackNode> nodes, Set<TrackNode> visited, boolean front) {
        extend(graph, nodes, visited, front, null);
    }

    public static void extend(TrackGraph graph, LinkedList<TrackNode> nodes, Set<TrackNode> visited,
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
    public static Map.Entry<TrackNode, TrackEdge> manualExit(TrackGraph graph, TravellingPoint probe, boolean forward,
            TravellingPoint.SteerDirection steer, List<Map.Entry<TrackNode, TrackEdge>> choices) {
        if (!forward) steer = switch (steer) {
            case LEFT -> TravellingPoint.SteerDirection.RIGHT;
            case RIGHT -> TravellingPoint.SteerDirection.LEFT;
            case NONE -> TravellingPoint.SteerDirection.NONE;
        };
        return probe.steer(steer, probe.edge.getNormal(graph, .5)).apply(graph, Pair.of(true, choices));
    }

    public static boolean isOwnTrack(TrackEdge edge) {
        var material = edge.getTrackMaterial();
        return CreateRailwayBackend.supports(material);
    }
}
