package willow.train.hexie.sync.engine;

import java.util.*;
import java.util.function.Function;
import willow.train.hexie.bridge.sync.RegionData.*;

/** One client cache and one server subscription per connection. Recovery always names a single page. */
public final class RegionSync {
    private RegionSync() {}

    public static final class Subscription {
        private final Function<RegionKey, RegionSnapshot> snapshotFor;
        private final Set<RegionKey> wanted = new HashSet<>();

        public Subscription(Function<RegionKey, RegionSnapshot> snapshotFor) { this.snapshotFor = Objects.requireNonNull(snapshotFor); }

        public RegionSnapshot subscribe(RegionKey key) {
            var snapshot = snapshotFor.apply(key);
            if (!key.equals(snapshot.page().key())) throw new IllegalArgumentException("Wrong subscription region");
            subscribe(snapshot);
            return snapshot;
        }

        public void subscribe(RegionSnapshot snapshot) { wanted.add(snapshot.page().key()); }

        public void unsubscribe(RegionKey key) { wanted.remove(key); }

        public List<RegionPatch> publish(Collection<RegionPatch> patches) {
            return patches.stream().filter(patch -> wanted.contains(patch.key())).toList();
        }

        public Optional<RegionSnapshot> recover(RegionKey key) {
            return wanted.contains(key) ? Optional.of(snapshotFor.apply(key)) : Optional.empty();
        }
    }

    public static final class Cache {
        private final Set<RegionKey> wanted = new HashSet<>();
        private final Map<RegionKey, RegionSnapshot> pages = new HashMap<>();

        public void subscribe(RegionKey key) { wanted.add(key); }

        public void unsubscribe(RegionKey key) {
            wanted.remove(key);
            pages.remove(key);
        }

        public Optional<RegionSnapshot> page(RegionKey key) { return Optional.ofNullable(pages.get(key)); }

        public void receive(RegionSnapshot snapshot) {
            var page = snapshot.page();
            if (!wanted.contains(page.key())) return;
            var previous = pages.get(page.key());
            if (previous == null || page.regionRevision() > previous.page().regionRevision()) {
                pages.put(page.key(), snapshot);
            }
        }

        public Optional<RegionKey> receive(RegionPatch patch) {
            if (!wanted.contains(patch.key())) return Optional.empty();
            var previous = pages.get(patch.key());
            if (previous == null) return Optional.of(patch.key());
            long revision = previous.page().regionRevision();
            if (patch.newRevision() <= revision) return Optional.empty();
            if (patch.baseRevision() != revision) return Optional.of(patch.key());
            pages.put(patch.key(), previous.apply(patch));
            return Optional.empty();
        }
    }
}
