package willow.train.hexie.vehicle.assembly;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** HeXie's bounded train-structure traversal. No game/mod types or assembly callbacks. */
public final class AssemblyTraversal<P> {
    public enum Cell { EMPTY, MOVABLE, BLOCKED }

    /** Describes block properties, not an externally computed assembled structure. */
    public interface Source<P> {
        boolean contains(P position);
        Cell cell(P position);
        boolean brittle(P position);
        List<P> attachments(P position);
        /** Mechanism-owned parts captured without expanding their neighbors (e.g. pulley ropes). */
        default List<P> capturedAttachments(P position) { return List.of(); }
        List<Face<P>> faces(P position);
    }

    public record Face<P>(P target, boolean movable, boolean brittle, boolean glued,
            boolean attached, boolean mutuallySticky, boolean pushOnly,
            boolean sourceSupportive, boolean targetSupportive) {
        public boolean connects() {
            if (!movable) return false;
            boolean sticky = !brittle && mutuallySticky && !pushOnly && sourceSupportive && targetSupportive;
            return glued || attached || sticky;
        }
    }

    private final Source<P> source;
    private final int blockLimit;
    private final int visitLimit;

    public AssemblyTraversal(Source<P> source, int blockLimit, int visitLimit) {
        if (blockLimit < 1 || visitLimit < 1) throw new IllegalArgumentException("Invalid scan limits");
        this.source = java.util.Objects.requireNonNull(source);
        this.blockLimit = blockLimit;
        this.visitLimit = visitLimit;
    }

    public List<P> scan(P root) {
        if (!source.contains(root)) throw new IllegalArgumentException("Root outside selection");
        if (source.brittle(root)) return List.of();
        var queue = new ArrayDeque<P>();
        Set<P> discovered = new LinkedHashSet<>();
        Set<P> result = new LinkedHashSet<>();
        queue.add(root);
        discovered.add(root);
        while (!queue.isEmpty()) {
            P position = queue.remove();
            Cell cell = source.cell(position);
            if (cell == Cell.EMPTY) continue;
            if (cell == Cell.BLOCKED) throw new IllegalStateException("Unmovable attached block: " + position);
            for (P attachment : source.attachments(position)) enqueue(attachment, queue, discovered);
            for (P attachment : source.capturedAttachments(position))
                if (source.contains(attachment)) result.add(attachment);
            for (Face<P> face : source.faces(position))
                if (face.connects()) enqueue(face.target(), queue, discovered);
            result.add(position);
            if (result.size() > blockLimit) throw new IllegalStateException("Assembly block limit exceeded");
        }
        return List.copyOf(result);
    }

    private void enqueue(P position, ArrayDeque<P> queue, Set<P> discovered) {
        if (!source.contains(position) || !discovered.add(position)) return;
        if (discovered.size() > visitLimit) throw new IllegalStateException("Assembly visit limit exceeded");
        queue.add(position);
    }
}
