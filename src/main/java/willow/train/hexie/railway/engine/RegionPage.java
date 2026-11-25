package willow.train.hexie.railway.engine;

import java.util.List;

public record RegionPage(RegionKey key, long regionRevision, List<Long> edgeIds, List<BoundaryPort> boundary) {
    public RegionPage {
        edgeIds = List.copyOf(edgeIds);
        boundary = List.copyOf(boundary);
    }
}
