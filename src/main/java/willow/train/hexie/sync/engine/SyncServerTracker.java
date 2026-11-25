package willow.train.hexie.sync.engine;

import java.util.*;
import java.util.function.Function;
import willow.train.hexie.sync.engine.SyncMessage.*;
import willow.train.hexie.bridge.sync.VehicleSyncData.CarriageContent;
import willow.train.hexie.bridge.sync.VehicleSyncData.TrainRecord;

/** World-level composition publisher. Each connection owns its own interest sequence space.
 * The server tick thread commits formation/content to its authoritative records before publishing. */
public final class SyncServerTracker {
    private final Function<UUID, ? extends CarriageContent> carriage;
    private final Map<UUID, List<TrainRecord.Member>> trains = new LinkedHashMap<>();
    private long compositionRevision;

    public SyncServerTracker(Function<UUID, ? extends CarriageContent> carriage) {
        this(carriage, 0, List.of());
    }

    public SyncServerTracker(Function<UUID, ? extends CarriageContent> carriage, long revision, Collection<TrainRecord> initial) {
        this.carriage = carriage;
        compositionRevision = revision;
        initial.forEach(t -> trains.put(t.trainId(), t.members()));
    }

    public Composition compose(Collection<TrainRecord> changed, List<UUID> removed) {
        return compose(compositionRevision + 1, changed, removed);
    }

    public Composition compose(long revision, Collection<TrainRecord> changed, List<UUID> removed) {
        var views = changed.stream().map(t -> view(t.trainId(), t.members())).toList();
        removed.forEach(trains::remove);
        changed.forEach(t -> trains.put(t.trainId(), t.members()));
        compositionRevision = revision;
        return new Composition(revision, views, removed);
    }

    /** Remove a retired carriage from any still-published membership before dropping its content. */
    public Optional<Composition> retire(UUID id) {
        var changed = new ArrayList<TrainRecord>();
        var removed = new ArrayList<UUID>();
        trains.forEach((train, members) -> {
            var remaining = members.stream().filter(member -> !id.equals(member.carriageId())).toList();
            if (remaining.size() == members.size()) return;
            if (remaining.isEmpty()) removed.add(train);
            else changed.add(new TrainRecord(train, remaining));
        });
        return changed.isEmpty() && removed.isEmpty() ? Optional.empty() : Optional.of(compose(changed, removed));
    }

    /** Initial membership for a joining client, including content edits since the last formation. */
    public Composition snapshot() {
        var views = trains.entrySet().stream().map(e -> view(e.getKey(), e.getValue())).toList();
        return new Composition(compositionRevision, views, List.of());
    }

    private TrainComposition view(UUID train, List<TrainRecord.Member> members) {
        var ids = members.stream().map(TrainRecord.Member::carriageId).toList();
        var revisions = ids.stream().map(id -> carriage.apply(id).contentRevision()).toList();
        return new TrainComposition(train, ids, members.stream().map(TrainRecord.Member::reversed).toList(), revisions);
    }

    public final class Client {
        private final Map<UUID, Long> sequences = new HashMap<>();
        private final Set<UUID> tracking = new HashSet<>();

        public boolean tracks(UUID id) { return tracking.contains(id); }

        /** Emits an Interest exactly once per transition. Leaving closes the current sequence. */
        public Optional<Interest> tracking(UUID id, boolean track) {
            if (track) {
                if (!tracking.add(id)) return Optional.empty();
                return Optional.of(new Interest(id, sequences.merge(id, 1L, Long::sum), true));
            }
            if (!tracking.remove(id)) return Optional.empty();
            return Optional.of(new Interest(id, sequences.get(id), false));
        }

        public Optional<Body> receive(GetBody request) {
            UUID id = request.carriageId();
            if (!tracking.contains(id) || sequences.get(id) != request.interestSeq()) return Optional.empty();
            var record = carriage.apply(id);
            if (record == null || request.haveContentRevision() != 0 && record.contentRevision() == request.haveContentRevision()) return Optional.empty();
            return Optional.of(new Body(id, request.interestSeq(), record.contentRevision(), BodyCells.encode(record.cells())));
        }

        /** Called with the actual changed cells after a content commit. Formation never calls this path. */
        public Optional<CellPatch> patch(UUID id, long baseRevision, long newRevision, List<CellChange> changes) {
            if (!tracking.contains(id)) return Optional.empty();
            return Optional.of(new CellPatch(id, sequences.get(id), baseRevision, newRevision, changes));
        }
    }
}
