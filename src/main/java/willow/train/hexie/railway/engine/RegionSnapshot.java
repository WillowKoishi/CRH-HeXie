package willow.train.hexie.railway.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public record RegionSnapshot(RegionPage page, Map<Long, EdgeGeometry> edges) {
    public RegionSnapshot {
        edges = Map.copyOf(edges);
        if (edges.size() != page.edgeIds().size() || !edges.keySet().containsAll(page.edgeIds())) {
            throw new IllegalArgumentException("Page and geometry disagree");
        }
    }

    public RegionSnapshot apply(RegionPatch patch) {
        if (!page.key().equals(patch.key()) || page.regionRevision() != patch.baseRevision()) {
            throw new IllegalArgumentException("Patch does not follow this page");
        }
        var updated = new LinkedHashMap<>(edges);
        patch.removed().forEach(updated::remove);
        updated.putAll(patch.changed());
        var next = new RegionPage(page.key(), patch.newRevision(), updated.keySet().stream().sorted().toList(),
                                  patch.boundary().orElse(page.boundary()));
        return new RegionSnapshot(next, updated);
    }
}
