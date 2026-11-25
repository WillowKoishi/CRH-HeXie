package willow.train.hexie.railway.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;

/** Resumable breadth-first search. The caller may restrict turns at diamonds and set switches. */
public final class RouteSearch {
    public static final int DEFAULT_BUDGET = 2048;
    private final RailwayGraph graph;
    private final ArcLengthTable.Cache lengths;
    private RouteCursor start;
    private DirectedEdge first;
    private final LongPredicate goalNode;
    private final BiPredicate<DirectedEdge, DirectedEdge> allowedTurn;
    private final ArrayDeque<DirectedEdge> queue = new ArrayDeque<>();
    private final Map<DirectedEdge, DirectedEdge> parent = new HashMap<>();
    private final Map<DirectedEdge, RouteCursor> entries = new HashMap<>();
    private final Set<DirectedEdge> waiting = new LinkedHashSet<>();
    private final Set<RegionKey> missing = new LinkedHashSet<>();
    private final Set<RegionKey> touched = new HashSet<>();
    private long generation;
    private DirectedEdge cycleTail;
    private SearchStep.DeadEnd deadEnd;
    private SearchStep result;

    public RouteSearch(RailwayGraph graph, ArcLengthTable.Cache lengths, RouteCursor start, LongPredicate goalNode) {
        this(graph, lengths, start, goalNode, (in, out) -> true);
    }

    public RouteSearch(RailwayGraph graph, ArcLengthTable.Cache lengths, RouteCursor start,
                       LongPredicate goalNode, BiPredicate<DirectedEdge, DirectedEdge> allowedTurn) {
        this.graph = graph;
        this.lengths = lengths;
        this.start = start;
        this.first = new DirectedEdge(start.edgeId(), start.direction());
        this.goalNode = goalNode;
        this.allowedTurn = allowedTurn;
        reset();
    }

    private void reset() {
        parent.clear();
        entries.clear();
        queue.clear();
        waiting.clear();
        missing.clear();
        touched.clear();
        result = null;
        cycleTail = null;
        deadEnd = null;
        parent.put(first, null);
        queue.add(first);
        touched.addAll(graph.regions(first.edgeId()));
        generation = graph.loadGeneration();
    }

    /** Feed the published region patches to active searches on the railway tick thread. */
    public boolean invalidate(RegionPatch patch) {
        return invalidate(patch, start);
    }

    /** Supply the train's rebound cursor when the edit removed its original edge. */
    public boolean invalidate(RegionPatch patch, RouteCursor currentHead) {
        return invalidate(patch.key(), currentHead);
    }

    public boolean invalidate(RegionKey changedRegion, RouteCursor currentHead) {
        if (!touched.contains(changedRegion)) return false;
        graph.geometry(currentHead.edgeId());
        start = currentHead;
        first = new DirectedEdge(start.edgeId(), start.direction());
        reset();
        return true;
    }

    public SearchStep tick() { return tick(DEFAULT_BUDGET); }

    public SearchStep tick(int budget) {
        if (budget <= 0) throw new IllegalArgumentException("Non-positive expansion budget");
        if (result != null) return result;
        if (generation != graph.loadGeneration()) {
            generation = graph.loadGeneration();
            queue.addAll(waiting);
            waiting.clear();
            missing.clear();
        }
        while (budget-- > 0 && !queue.isEmpty()) {
            var in = queue.removeFirst();
            if (awaitRegions(in, graph.regions(in.edgeId()))) continue;
            var edge = graph.geometry(in.edgeId());
            boolean forward = in.direction() == Direction.FORWARD;
            long node = forward ? edge.toNode() : edge.fromNode();
            entries.put(in, in.equals(first) ? start : in.at(forward ? 0 : lengths.get(edge).lengthM()));
            var turnout = graph.switchRegion(node);
            if (turnout != null) touched.add(turnout);
            if (goalNode.test(node)) {
                var path = chain(in).stream().map(entries::get).toList();
                return result = new SearchStep.Found(path);
            }
            boolean blocked = awaitRegions(in, graph.neighbors(node));
            boolean connected = false;
            for (var out : graph.exits(node)) {
                if (out.equals(in.reverse()) || !allowedTurn.test(in, out)) continue;
                connected = true;
                if (out.equals(first)) {
                    if (cycleTail == null) cycleTail = in;
                } else if (!parent.containsKey(out)) {
                    touched.addAll(graph.regions(out.edgeId()));
                    queue.addLast(out);
                    parent.put(out, in);
                }
            }
            if (!connected && !blocked && deadEnd == null && graph.endState(in) == EndState.TRUE_END) {
                deadEnd = new SearchStep.DeadEnd(node);
            }
        }
        if (!queue.isEmpty()) return new SearchStep.Continue();
        if (!missing.isEmpty()) return new SearchStep.Pending(missing);
        if (cycleTail != null) return result = new SearchStep.Cycle(chain(cycleTail).stream().map(graph::directedId).toList());
        if (deadEnd != null) return result = deadEnd;
        // A switch can lead into a non-start circuit or block all exits. Wait for a route edit.
        return new SearchStep.Continue();
    }

    private boolean awaitRegions(DirectedEdge edge, Set<RegionKey> regions) {
        touched.addAll(regions);
        boolean blocked = false;
        for (var key : regions) {
            if (graph.availability(key) != EndState.CONNECTED) {
                waiting.add(edge);
                missing.add(key);
                blocked = true;
            }
        }
        return blocked;
    }

    private List<DirectedEdge> chain(DirectedEdge tail) {
        var path = new ArrayList<DirectedEdge>();
        touched.clear();
        for (var cursor = tail; cursor != null; cursor = parent.get(cursor)) {
            path.add(cursor);
            touched.addAll(graph.regions(cursor.edgeId()));
            var edge = graph.geometry(cursor.edgeId());
            var turnout = graph.switchRegion(cursor.direction() == Direction.FORWARD ? edge.toNode() : edge.fromNode());
            if (turnout != null) touched.add(turnout);
        }
        Collections.reverse(path);
        return path;
    }
}
