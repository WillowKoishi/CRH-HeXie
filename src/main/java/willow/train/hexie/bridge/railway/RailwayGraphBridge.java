package willow.train.hexie.bridge.railway;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.DoubleUnaryOperator;
import java.util.function.LongPredicate;
import willow.train.hexie.core.api.vehicle.Point3;

/** Region-paged railway data. Use a network and its searches on the owning level's tick thread. */
public interface RailwayGraphBridge {
    /** All callers sharing the same level object, namespaced dimension ID and network ID see the same graph. */
    Network network(Object level, String dimensionId, long networkId);
    /** Release the networks and their geometry caches when the backend unloads its level. */
    void releaseLevel(Object level);
    /** Sample an existing backend's track centreline for import into a graph. */
    Edge measureEdge(long edgeId, long fromNode, long toNode, long revision, TrackGeometryView geometry);

    record Region(int x, int z) {}
    record Boundary(long nodeId, Region neighbor) {}
    enum Availability { CONNECTED, UNLOADED, NOT_FETCHED }

    /** Canonical polyline in world metres, ordered from fromNode to toNode. */
    record Edge(long edgeId, long fromNode, long toNode, long geometryRevision, List<Point3> samples) {
        public Edge { samples = List.copyOf(samples); }
    }
    record Track(Edge geometry, Set<Long> chunks) {
        public Track { chunks = Set.copyOf(chunks); }
    }
    record Page(Region region, long revision, Map<Long, Edge> edges, List<Boundary> boundary) {
        public Page { edges = Map.copyOf(edges); boundary = List.copyOf(boundary); }
    }
    record Patch(Region region, long baseRevision, long newRevision, Map<Long, Edge> changed,
                 Set<Long> removed, Optional<List<Boundary>> boundary) {
        public Patch {
            changed = Map.copyOf(changed);
            removed = Set.copyOf(removed);
            boundary = boundary.map(List::copyOf);
        }
    }
    record Traversal(long edgeId, boolean reversed) {}
    /** Distance is always measured from the canonical start of the edge. */
    record Cursor(long edgeId, double sM, boolean reversed) {}

    interface Network {
        /** Chunk keys pack signed x in the low 32 bits and signed z in the high 32 bits. */
        Region region(long chunkKey);
        /** Initial import of an edge ID. Subsequent block changes and reinsertions use editChunk. */
        void addEdge(Track track);
        void loadPage(Region region, long revision, List<Boundary> boundary);
        void setAvailability(Region region, Availability availability);
        void locateSwitch(long nodeId, Region region);
        List<Patch> editChunk(long chunkKey, Collection<Track> replacements, Set<Long> removed,
                              Map<Region, List<Boundary>> boundaries);
        Page snapshot(Region region);
        ArcTable arcLength(long edgeId);
        Search search(Cursor start, LongPredicate goal, BiPredicate<Traversal, Traversal> allowedTurn);
        SignalIndex signalPathIndex(List<Traversal> route, Collection<Signal> signals, boolean closed);
    }

    /** A measured polyline that can also be supplied directly to trackRoute(). Parameter = metres / length. */
    interface ArcTable extends TrackGeometryView {
        int sampleCount();
        Sample sampleMetres(double metres, boolean reversed);
    }

    interface Search {
        Step tick();
        Step tick(int edgeBudget);
        /** Supply the current cursor when an edit removes or rebinds the train's original edge. */
        boolean invalidate(Patch patch, Cursor head);
    }
    sealed interface Step {
        record Found(List<Cursor> path) implements Step {
            public Found { path = List.copyOf(path); }
        }
        record Continue() implements Step {}
        record Pending(Set<Region> missing) implements Step {
            public Pending { missing = Set.copyOf(missing); }
        }
        record DeadEnd(long nodeId) implements Step {}
        record Cycle(List<Traversal> edges) implements Step {
            public Cycle { edges = List.copyOf(edges); }
        }
    }

    record Signal(long signalId, String systemId, long edgeId, double sM, boolean reversed) {}
    interface SignalIndex {
        SignalWindow newWindow();
    }
    interface SignalWindow {
        Delta slide(double headM, boolean reversed, double sightM, double speedMps,
                    DoubleUnaryOperator brakingDistance, double marginM);
    }
    record Delta(Set<Signal> subscribe, Set<Signal> unsubscribe) {
        public Delta { subscribe = Set.copyOf(subscribe); unsubscribe = Set.copyOf(unsubscribe); }
    }
}
