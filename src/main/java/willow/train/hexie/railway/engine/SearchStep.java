package willow.train.hexie.railway.engine;

import java.util.List;
import java.util.Set;

public sealed interface SearchStep {
    /** One cursor per traversed edge: the original position, then the entry position of each next edge. */
    record Found(List<RouteCursor> path) implements SearchStep {
        public Found { path = List.copyOf(path); }
    }
    record Continue() implements SearchStep {}
    record Pending(Set<RegionKey> missing) implements SearchStep {
        public Pending { missing = Set.copyOf(missing); }
    }
    record DeadEnd(long nodeId) implements SearchStep {}
    /** Network-local directed IDs in traversal order. Resolve each with RailwayGraph.directedEdge. */
    record Cycle(List<Long> directedEdges) implements SearchStep {
        public Cycle { directedEdges = List.copyOf(directedEdges); }
    }
}
