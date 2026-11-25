package willow.train.hexie.railway.engine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** An absent boundary keeps the old ports. A turnout-only invalidation can have an empty payload. */
public record RegionPatch(RegionKey key, long baseRevision, long newRevision,
                          Map<Long, EdgeGeometry> changed, Set<Long> removed,
                          Optional<List<BoundaryPort>> boundary) {
    public RegionPatch {
        if (newRevision <= baseRevision) throw new IllegalArgumentException("Region revision must advance");
        changed = Map.copyOf(changed);
        removed = Set.copyOf(removed);
        boundary = boundary.map(List::copyOf);
    }
}
