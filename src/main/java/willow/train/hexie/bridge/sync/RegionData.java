package willow.train.hexie.bridge.sync;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import willow.train.hexie.bridge.sync.VehicleSyncData.Id;
import willow.train.hexie.core.api.vehicle.Point3;

public final class RegionData {
    private RegionData() {}

    public record RegionKey(Id dimensionId, long networkId, int regionX, int regionZ) {
        public static RegionKey fromChunk(Id dimension, long network, int chunkX, int chunkZ) {
            return new RegionKey(dimension, network, Math.floorDiv(chunkX, 16), Math.floorDiv(chunkZ, 16));
        }

        public static long chunkKey(int chunkX, int chunkZ) {
            return Integer.toUnsignedLong(chunkX) | ((long) chunkZ << 32);
        }
    }

    public record BoundaryPort(long nodeId, RegionKey neighbor) {}

    /** Ordered polyline samples run from fromNode to toNode in world metres. */
    public record EdgeGeometry(long edgeId, long fromNode, long toNode, long geometryRevision, List<Point3> samples) {
        public EdgeGeometry {
            samples = List.copyOf(samples);
            if (samples.size() < 2) {
                throw new IllegalArgumentException("An edge needs at least two samples");
            }
        }
    }

    public record RegionPage(RegionKey key, long regionRevision, List<Long> edgeIds, List<BoundaryPort> boundary) {
        public RegionPage {
            edgeIds = List.copyOf(edgeIds);
            boundary = List.copyOf(boundary);
        }
    }

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
}
