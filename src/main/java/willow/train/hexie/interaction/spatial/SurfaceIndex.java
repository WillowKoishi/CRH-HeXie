package willow.train.hexie.interaction.spatial;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import willow.train.hexie.interaction.spatial.model.AABB;
import willow.train.hexie.interaction.spatial.model.Cell;
import willow.train.hexie.interaction.spatial.model.Face;
import willow.train.hexie.interaction.spatial.model.SurfaceAttachment;
import willow.train.hexie.interaction.spatial.model.SurfaceHostId;

/** Indexes attachments sharing a host cell and face. All writes and reads belong to the owning world thread. */
public final class SurfaceIndex {
    /** Full-motion envelopes in declaration order. A missing envelope always requires an exact test. */
    public static final class PartBounds {
        private final AabbTree<Integer> tree = new AabbTree<>();
        private final BitSet unbounded = new BitSet();

        public PartBounds(List<AABB> bounds) {
            for (int i = 0; i < bounds.size(); i++) {
                AABB box = bounds.get(i);
                if (box == null) unbounded.set(i);
                else tree.insert(box, i);
            }
        }

        BitSet candidates(PickGeometry.Ray ray, double reach) {
            BitSet result = (BitSet) unbounded.clone();
            tree.query(ray, new SpatialPicker.Query(reach), result::set);
            return result;
        }
    }

    private record Key(SurfaceHostId address, Face face) {}

    private final Map<Key, Set<UUID>> byFace = new HashMap<>();
    private final Map<UUID, SurfaceAttachment> byId = new HashMap<>();

    public Set<UUID> at(SurfaceHostId host, Cell cell, Face face) {
        var ids = byFace.get(new Key(host.at(cell), face));
        return ids == null ? Set.of() : Set.copyOf(ids);
    }

    public Optional<SurfaceAttachment> get(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public void put(SurfaceAttachment attachment) {
        UUID id = attachment.attachmentId();
        remove(id);
        byId.put(id, attachment);
        keys(attachment).forEach(key -> byFace.computeIfAbsent(key, unused -> new HashSet<>()).add(id));
    }

    public Optional<SurfaceAttachment> remove(UUID id) {
        var removed = byId.remove(id);
        if (removed != null) {
            keys(removed).forEach(key -> {
                var ids = byFace.get(key);
                ids.remove(id);
                if (ids.isEmpty()) byFace.remove(key);
            });
        }
        return Optional.ofNullable(removed);
    }

    private static Stream<Key> keys(SurfaceAttachment attachment) {
        var anchor = attachment.anchor();
        return Stream.concat(Stream.of(anchor.cell()), attachment.spanCells().stream())
                .distinct().map(cell -> new Key(anchor.at(cell), attachment.face()));
    }
}
