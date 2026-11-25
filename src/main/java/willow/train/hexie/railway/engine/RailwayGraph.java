package willow.train.hexie.railway.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Index for one dimension/network. Owned by the railway tick thread. */
public final class RailwayGraph {
    /** chunks is the backend's full footprint, including intermediate chunks of long edges. */
    public record TrackEdge(EdgeGeometry geometry, Set<Long> chunks) {
        public TrackEdge {
            chunks = Set.copyOf(chunks);
            if (chunks.isEmpty()) throw new IllegalArgumentException("Missing track footprint");
        }
    }

    private static final class Page {
        long revision;
        Set<Long> edges = new LinkedHashSet<>();
        List<BoundaryPort> boundary = List.of();
        EndState availability = EndState.CONNECTED;
    }

    private final Id dimension;
    private final long network;
    private final Map<Long, TrackEdge> edges = new HashMap<>();
    // Stable slots let a directed ID fit in one long even when the backend uses all 64 edge-ID bits.
    private record EdgeVersion(long revision, int slot) {}
    private final Map<Long, EdgeVersion> history = new HashMap<>();
    private final List<Long> backendIds = new ArrayList<>();
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();
    private final Map<RegionKey, Page> pages = new HashMap<>();
    private final Map<Long, Set<DirectedEdge>> exits = new HashMap<>();
    private final Map<Long, Map<RegionKey, List<RegionKey>>> portsByNode = new HashMap<>();
    private final Map<Long, RegionKey> switchRegions = new HashMap<>();
    private long loadGeneration;

    public RailwayGraph(Id dimension, long network) {
        this.dimension = dimension;
        this.network = network;
    }

    public RegionKey region(long chunkKey) {
        return RegionKey.fromChunk(dimension, network, (int) chunkKey, (int) (chunkKey >> 32));
    }

    Set<RegionKey> regions(long edgeId) {
        var track = edges.get(edgeId);
        if (track == null) throw new IllegalArgumentException("Route cursor needs a current edge: " + edgeId);
        return regions(track);
    }

    private Set<RegionKey> regions(TrackEdge edge) {
        var keys = new LinkedHashSet<RegionKey>();
        for (long chunk : edge.chunks()) keys.add(region(chunk));
        return keys;
    }

    /** Initial import. Runtime track edits go through replaceChunk. */
    public void addEdge(TrackEdge track) {
        long id = track.geometry().edgeId();
        if (edges.containsKey(id)) throw new IllegalArgumentException("Duplicate edge " + id);
        index(revised(track));
        loadGeneration++;
    }

    private TrackEdge revised(TrackEdge track) {
        var g = track.geometry();
        var previous = history.get(g.edgeId());
        if (previous == null) return track;
        var geometry = new EdgeGeometry(g.edgeId(), g.fromNode(), g.toNode(), previous.revision() + 1, g.samples());
        return new TrackEdge(geometry, track.chunks());
    }

    private void index(TrackEdge track) {
        var geometry = track.geometry();
        long id = geometry.edgeId();
        edges.put(id, track);
        var previous = history.get(id);
        int slot = previous == null ? backendIds.size() : previous.slot();
        if (previous == null) backendIds.add(id);
        history.put(id, new EdgeVersion(geometry.geometryRevision(), slot));
        for (long chunk : track.chunks()) byChunk.computeIfAbsent(chunk, k -> new LinkedHashSet<>()).add(id);
        for (var key : regions(track)) page(key).edges.add(id);
        exits.computeIfAbsent(geometry.fromNode(), k -> new LinkedHashSet<>()).add(new DirectedEdge(id, Direction.FORWARD));
        exits.computeIfAbsent(geometry.toNode(), k -> new LinkedHashSet<>()).add(new DirectedEdge(id, Direction.BACKWARD));
    }

    private void unindex(TrackEdge track) {
        var geometry = track.geometry();
        long id = geometry.edgeId();
        edges.remove(id);
        for (long chunk : track.chunks()) removeFrom(byChunk, chunk, id);
        for (var key : regions(track)) page(key).edges.remove(id);
        removeFrom(exits, geometry.fromNode(), new DirectedEdge(id, Direction.FORWARD));
        removeFrom(exits, geometry.toNode(), new DirectedEdge(id, Direction.BACKWARD));
    }

    private static <T> void removeFrom(Map<Long, Set<T>> index, long key, T value) {
        var bucket = index.get(key);
        bucket.remove(value);
        if (bucket.isEmpty()) index.remove(key);
    }

    private Page page(RegionKey key) {
        if (!dimension.equals(key.dimensionId()) || network != key.networkId()) {
            throw new IllegalArgumentException("Foreign region " + key);
        }
        return pages.computeIfAbsent(key, k -> new Page());
    }

    public void loadPage(RegionKey key, long revision, List<BoundaryPort> ports) {
        var page = page(key);
        page.revision = revision;
        page.availability = EndState.CONNECTED;
        setBoundary(key, ports);
        loadGeneration++;
    }

    private void setBoundary(RegionKey key, List<BoundaryPort> ports) {
        var page = page(key);
        for (var old : page.boundary) {
            var owners = portsByNode.get(old.nodeId());
            if (owners != null) {
                owners.remove(key);
                if (owners.isEmpty()) portsByNode.remove(old.nodeId());
            }
        }
        page.boundary = List.copyOf(ports);
        for (var port : ports) {
            portsByNode.computeIfAbsent(port.nodeId(), n -> new HashMap<>())
                       .computeIfAbsent(key, k -> new ArrayList<>()).add(port.neighbor());
        }
    }

    public void setAvailability(RegionKey region, EndState state) {
        if (state == EndState.TRUE_END) throw new IllegalArgumentException("TRUE_END describes a node");
        page(region).availability = state;
        loadGeneration++;
    }

    EndState availability(RegionKey key) {
        Page page = pages.get(key);
        return page == null ? EndState.NOT_FETCHED : page.availability;
    }

    long loadGeneration() { return loadGeneration; }

    Set<RegionKey> neighbors(long node) {
        var result = new LinkedHashSet<RegionKey>();
        var at = portsByNode.get(node);
        if (at != null) at.values().forEach(result::addAll);
        return result;
    }

    Set<DirectedEdge> exits(long node) { return exits.getOrDefault(node, Set.of()); }

    long directedId(DirectedEdge edge) {
        return 2L * history.get(edge.edgeId()).slot() + (edge.direction() == Direction.FORWARD ? 0 : 1);
    }

    /** Resolves an entry of SearchStep.Cycle in the network that produced it. */
    public DirectedEdge directedEdge(long directedId) {
        return new DirectedEdge(backendIds.get(Math.toIntExact(directedId >>> 1)),
                                (directedId & 1) == 0 ? Direction.FORWARD : Direction.BACKWARD);
    }

    RegionKey switchRegion(long node) { return switchRegions.get(node); }

    public EndState endState(DirectedEdge incoming) {
        var geometry = geometry(incoming.edgeId());
        long node = incoming.direction() == Direction.FORWARD ? geometry.toNode() : geometry.fromNode();
        for (var region : neighbors(node)) {
            var state = availability(region);
            if (state != EndState.CONNECTED) return state;
        }
        var incident = exits(node);
        return incident.size() == 1 && incident.contains(incoming.reverse()) ? EndState.TRUE_END : EndState.CONNECTED;
    }

    public void locateSwitch(long nodeId, RegionKey key) {
        page(key);
        switchRegions.put(nodeId, key);
    }

    public Set<Long> edgesInChunk(long chunk) { return Set.copyOf(byChunk.getOrDefault(chunk, Set.of())); }

    public EdgeGeometry geometry(long id) {
        TrackEdge edge = edges.get(id);
        if (edge == null) throw new IllegalArgumentException("Unknown edge " + id);
        return edge.geometry();
    }

    public RegionSnapshot snapshot(RegionKey key) {
        Page page = pages.get(key);
        if (page == null || page.availability != EndState.CONNECTED) throw new IllegalStateException("Missing page " + key);
        var geometry = new LinkedHashMap<Long, EdgeGeometry>();
        for (long id : page.edges) geometry.put(id, geometry(id));
        return new RegionSnapshot(new RegionPage(key, page.revision, page.edges.stream().sorted().toList(), page.boundary), geometry);
    }

    /** The backend supplies the new contents of just this chunk (including moved tracks' full footprints).
     * An edge leaving the chunk can be retained by including its replacement at the new location. */
    public List<RegionPatch> replaceChunk(long chunk, Collection<TrackEdge> replacements,
                                          Map<RegionKey, List<BoundaryPort>> changedBoundaries) {
        var removed = new LinkedHashSet<>(byChunk.getOrDefault(chunk, Set.of()));
        for (var next : replacements) removed.remove(next.geometry().edgeId());
        return editChunk(chunk, replacements, removed, changedBoundaries);
    }

    /** A block or backend event with exact hits can leave other tracks in the same chunk alone. */
    public List<RegionPatch> editChunk(long chunk, Collection<TrackEdge> replacements,
                                       Set<Long> removedIds, Map<RegionKey, List<BoundaryPort>> changedBoundaries) {
        var bucket = byChunk.getOrDefault(chunk, Set.of());
        if (!bucket.containsAll(removedIds)) throw new IllegalArgumentException("Removal outside edited chunk");
        var affected = new LinkedHashSet<>(removedIds);
        var incoming = new LinkedHashMap<Long, TrackEdge>();
        for (TrackEdge track : replacements) {
            long id = track.geometry().edgeId();
            if (!bucket.contains(id) && !track.chunks().contains(chunk)) throw new IllegalArgumentException("Unrelated edge " + id);
            if (incoming.put(id, track) != null) throw new IllegalArgumentException("Duplicate replacement " + id);
        }
        affected.addAll(incoming.keySet());
        var changed = new LinkedHashMap<RegionKey, Map<Long, EdgeGeometry>>();
        var removed = new HashMap<RegionKey, Set<Long>>();
        var dirty = new LinkedHashSet<RegionKey>();
        var boundaries = new LinkedHashMap<RegionKey, List<BoundaryPort>>();
        changedBoundaries.forEach((key, ports) -> {
            var immutable = List.copyOf(ports);
            if (!page(key).boundary.equals(immutable)) {
                boundaries.put(key, immutable);
                dirty.add(key);
            }
        });
        for (long id : affected) {
            TrackEdge old = edges.get(id);
            TrackEdge next = incoming.get(id);
            Set<RegionKey> nextKeys = next == null ? Set.of() : regions(next);
            if (old != null) {
                touchSwitches(old, dirty);
                for (var key : regions(old)) {
                    dirty.add(key);
                    if (!nextKeys.contains(key)) removed.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(id);
                }
                unindex(old);
            }
            if (next != null) {
                next = revised(next);
                index(next);
                touchSwitches(next, dirty);
                for (var key : nextKeys) {
                    dirty.add(key);
                    changed.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(id, next.geometry());
                }
            }
        }
        var patches = new ArrayList<RegionPatch>();
        for (var key : dirty) {
            var page = page(key);
            var boundary = Optional.ofNullable(boundaries.get(key));
            boundary.ifPresent(ports -> setBoundary(key, ports));
            long base = page.revision++;
            patches.add(new RegionPatch(key, base, page.revision, changed.getOrDefault(key, Map.of()),
                                        removed.getOrDefault(key, Set.of()), boundary));
        }
        if (!dirty.isEmpty()) loadGeneration++;
        return List.copyOf(patches);
    }

    private void touchSwitches(TrackEdge edge, Set<RegionKey> dirty) {
        var g = edge.geometry();
        for (long node : new long[]{g.fromNode(), g.toNode()}) {
            var region = switchRegions.get(node);
            if (region != null) dirty.add(region);
        }
    }
}
