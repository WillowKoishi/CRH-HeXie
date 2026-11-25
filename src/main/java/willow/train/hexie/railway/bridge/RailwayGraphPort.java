package willow.train.hexie.railway.bridge;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;
import willow.train.hexie.bridge.railway.RailwayGraphBridge;
import willow.train.hexie.bridge.railway.TrackGeometryView;
import willow.train.hexie.railway.engine.*;
import willow.train.hexie.railway.graph.GraphTrackGeometry;

final class RailwayGraphPort implements RailwayGraphBridge {
    private record NetworkKey(Id dimension, long networkId) {}
    private final Map<Object, Map<NetworkKey, Network>> levels = new IdentityHashMap<>();

    @Override
    public synchronized Network network(Object level, String dimensionId, long networkId) {
        var key = new NetworkKey(Id.parse(dimensionId), networkId);
        return levels.computeIfAbsent(Objects.requireNonNull(level), ignored -> new HashMap<>())
                .computeIfAbsent(key, NetworkPort::new);
    }

    @Override
    public synchronized void releaseLevel(Object level) {
        levels.remove(level);
    }

    @Override
    public Edge measureEdge(long edgeId, long fromNode, long toNode, long revision, TrackGeometryView geometry) {
        return new Edge(edgeId, fromNode, toNode, revision, GraphTrackGeometry.centreline(geometry));
    }

    private static final class NetworkPort implements Network {
        private final NetworkKey key;
        private final RailwayGraph graph;
        // Edge IDs and revisions are unique within this network.
        private final ArcLengthTable.Cache lengths = new ArcLengthTable.Cache(256);

        NetworkPort(NetworkKey key) {
            this.key = key;
            graph = new RailwayGraph(key.dimension(), key.networkId());
        }

        private RegionKey regionKey(Region region) {
            return new RegionKey(key.dimension(), key.networkId(), region.x(), region.z());
        }

        private List<BoundaryPort> ports(List<Boundary> boundary) {
            return boundary.stream().map(b -> new BoundaryPort(b.nodeId(), regionKey(b.neighbor()))).toList();
        }

        @Override public Region region(long chunkKey) { return coordinates(graph.region(chunkKey)); }
        @Override public void addEdge(Track track) { graph.addEdge(trackEdge(track)); }
        @Override public void locateSwitch(long nodeId, Region region) { graph.locateSwitch(nodeId, regionKey(region)); }

        @Override
        public void loadPage(Region region, long revision, List<Boundary> boundary) {
            graph.loadPage(regionKey(region), revision, ports(boundary));
        }

        @Override
        public void setAvailability(Region region, Availability availability) {
            graph.setAvailability(regionKey(region), switch (availability) {
                case CONNECTED -> EndState.CONNECTED;
                case UNLOADED -> EndState.UNLOADED;
                case NOT_FETCHED -> EndState.NOT_FETCHED;
            });
        }

        @Override
        public List<Patch> editChunk(long chunkKey, Collection<Track> replacements, Set<Long> removed,
                                     Map<Region, List<Boundary>> boundaries) {
            var changedBoundaries = new HashMap<RegionKey, List<BoundaryPort>>();
            boundaries.forEach((region, boundary) -> changedBoundaries.put(regionKey(region), ports(boundary)));
            var tracks = replacements.stream().map(RailwayGraphPort::trackEdge).toList();
            return graph.editChunk(chunkKey, tracks, removed, changedBoundaries).stream()
                    .map(p -> new Patch(coordinates(p.key()), p.baseRevision(), p.newRevision(),
                            edges(p.changed()), p.removed(), p.boundary().map(RailwayGraphPort::boundaries)))
                    .toList();
        }

        @Override
        public Page snapshot(Region region) {
            var snapshot = graph.snapshot(regionKey(region));
            return new Page(region, snapshot.page().regionRevision(), edges(snapshot.edges()),
                    boundaries(snapshot.page().boundary()));
        }

        @Override
        public ArcTable arcLength(long edgeId) {
            return new GraphTrackGeometry(lengths.get(graph.geometry(edgeId)));
        }

        @Override
        public Search search(Cursor start, LongPredicate goal, BiPredicate<Traversal, Traversal> allowedTurn) {
            var search = new RouteSearch(graph, lengths, cursor(start), goal,
                    (in, out) -> allowedTurn.test(traversal(in), traversal(out)));
            return new Search() {
                @Override public Step tick() { return tick(RouteSearch.DEFAULT_BUDGET); }
                @Override public Step tick(int edgeBudget) {
                    return switch (search.tick(edgeBudget)) {
                        case SearchStep.Continue step -> new Step.Continue();
                        case SearchStep.Pending step -> new Step.Pending(step.missing().stream()
                                .map(RailwayGraphPort::coordinates).collect(Collectors.toSet()));
                        case SearchStep.DeadEnd step -> new Step.DeadEnd(step.nodeId());
                        case SearchStep.Found step -> new Step.Found(step.path().stream()
                                .map(c -> new Cursor(c.edgeId(), c.sM(), c.direction() == Direction.BACKWARD)).toList());
                        case SearchStep.Cycle step -> new Step.Cycle(step.directedEdges().stream()
                                .map(graph::directedEdge).map(RailwayGraphPort::traversal).toList());
                    };
                }
                @Override public boolean invalidate(Patch patch, Cursor head) {
                    return search.invalidate(regionKey(patch.region()), cursor(head));
                }
            };
        }

        @Override
        public SignalIndex signalPathIndex(List<Traversal> route, Collection<Signal> signals, boolean closed) {
            var legs = route.stream().map(t -> new SignalPathIndex.Leg(
                    new DirectedEdge(t.edgeId(), direction(t.reversed())), lengths.get(graph.geometry(t.edgeId())).lengthM())).toList();
            var anchors = signals.stream().map(s -> new SignalAnchor(s.signalId(), Id.parse(s.systemId()),
                    s.edgeId(), s.sM(), direction(s.reversed()))).toList();
            var index = new SignalPathIndex(legs, anchors, closed);
            return () -> {
                var window = index.new Window();
                return (headM, reversed, sightM, speedMps, brakingDistance, marginM) -> {
                    var delta = window.slide(headM, direction(reversed), sightM, speedMps, brakingDistance, marginM);
                    return new Delta(signals(delta.subscribe()), signals(delta.unsubscribe()));
                };
            };
        }
    }

    private static RailwayGraph.TrackEdge trackEdge(Track track) {
        var e = track.geometry();
        return new RailwayGraph.TrackEdge(new EdgeGeometry(e.edgeId(), e.fromNode(), e.toNode(),
                e.geometryRevision(), e.samples()), track.chunks());
    }

    private static Map<Long, Edge> edges(Map<Long, EdgeGeometry> geometry) {
        var result = new HashMap<Long, Edge>();
        geometry.forEach((id, e) -> result.put(id, new Edge(id, e.fromNode(), e.toNode(), e.geometryRevision(), e.samples())));
        return result;
    }

    private static Region coordinates(RegionKey key) { return new Region(key.regionX(), key.regionZ()); }
    private static Direction direction(boolean reversed) { return reversed ? Direction.BACKWARD : Direction.FORWARD; }
    private static Traversal traversal(DirectedEdge e) { return new Traversal(e.edgeId(), e.direction() == Direction.BACKWARD); }
    private static RouteCursor cursor(Cursor c) { return new RouteCursor(c.edgeId(), c.sM(), direction(c.reversed())); }

    private static List<Boundary> boundaries(List<BoundaryPort> ports) {
        return ports.stream().map(p -> new Boundary(p.nodeId(), coordinates(p.neighbor()))).toList();
    }

    private static Set<Signal> signals(Set<SignalAnchor> anchors) {
        return anchors.stream().map(a -> new Signal(a.signalId(), a.systemId().toString(),
                a.edgeId(), a.sM(), a.facing() == Direction.BACKWARD)).collect(Collectors.toSet());
    }
}
