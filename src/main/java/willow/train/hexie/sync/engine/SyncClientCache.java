package willow.train.hexie.sync.engine;

import java.util.*;
import willow.train.hexie.sync.engine.SyncMessage.*;

/** One instance per client world. Receivers and background completions publish under the same lock. */
public final class SyncClientCache<B> {
    private long latestCompositionRevision = -1;
    private final Map<UUID, MemberView> membership = new HashMap<>();
    private final Map<UUID, CarriageBody<B>> bodies = new HashMap<>();
    private final Map<UUID, Long> trackingSeq = new HashMap<>();
    private final Set<UUID> active = new HashSet<>();

    public synchronized Map<UUID, MemberView> membership() { return Map.copyOf(membership); }
    public synchronized long latestCompositionRevision() { return latestCompositionRevision; }
    public synchronized Optional<CarriageBody<B>> body(UUID id) { return Optional.ofNullable(bodies.get(id)); }

    public synchronized Optional<GetBody> receive(Interest interest) {
        UUID id = interest.carriageId();
        long seq = interest.interestSeq();
        long previous = trackingSeq.getOrDefault(id, -1L);
        if (seq < previous || interest.tracking() && seq == previous) return Optional.empty();
        trackingSeq.put(id, seq);
        bodies.remove(id);
        if (!interest.tracking()) {
            active.remove(id);
            return Optional.empty();
        }
        active.add(id);
        return membership.containsKey(id) ? Optional.of(request(id)) : Optional.empty();
    }

    public synchronized List<GetBody> receive(Composition composition) {
        if (composition.compositionRevision() <= latestCompositionRevision) return List.of();
        var affectedTrains = new HashSet<>(composition.removed());
        var incoming = new LinkedHashMap<UUID, MemberView>();
        for (var train : composition.changed()) {
            affectedTrains.add(train.trainId());
            for (int i = 0; i < train.orderedCarriages().size(); i++) {
                var id = train.orderedCarriages().get(i);
                var old = membership.get(id);
                long revision = train.contentRevisions().get(i);
                if (old != null) revision = Math.max(revision, old.contentRevision());
                var member = new MemberView(train.trainId(), i, train.reversed().get(i), revision);
                if (incoming.put(id, member) != null) throw new IllegalArgumentException("Duplicate member " + id);
            }
        }
        membership.values().removeIf(member -> affectedTrains.contains(member.trainId()));
        membership.putAll(incoming);
        latestCompositionRevision = composition.compositionRevision();
        var requests = new ArrayList<GetBody>();
        incoming.forEach((id, member) -> {
            var body = bodies.get(id);
            if (active.contains(id) && (body == null || body.contentRevision() < member.contentRevision())) {
                requests.add(request(id));
            }
        });
        return List.copyOf(requests);
    }

    public synchronized boolean receive(Body packet) {
        UUID id = packet.carriageId();
        if (!tracking(id, packet.interestSeq())) return false;
        var current = bodies.get(id);
        if (current != null && current.contentRevision() >= packet.contentRevision()) return false;
        var member = membership.get(id);
        if (member != null && packet.contentRevision() < member.contentRevision()) return false;
        install(id, new CarriageBody<>(packet.contentRevision(), BodyCells.decode(packet.cells()), Optional.empty()));
        return true;
    }

    public synchronized Optional<GetBody> receive(CellPatch patch) {
        UUID id = patch.carriageId();
        if (!tracking(id, patch.interestSeq())) return Optional.empty();
        var current = bodies.get(id);
        if (current != null && patch.newRevision() <= current.contentRevision()) return Optional.empty();
        if (current == null || current.contentRevision() != patch.baseRevision()) return Optional.of(request(id));
        var cells = new HashMap<>(current.cells());
        for (var change : patch.changes()) {
            if (change.block().isPresent()) cells.put(change.cell(), change.block().get());
            else cells.remove(change.cell());
        }
        install(id, new CarriageBody<>(patch.newRevision(), cells, Optional.empty()));
        return Optional.empty();
    }

    /** The worker captures both versions at submission. Calling this method is the install callback. */
    public synchronized boolean installBaked(UUID id, long seq, long contentRevision, B baked) {
        if (!tracking(id, seq)) return false;
        var current = bodies.get(id);
        if (current == null || current.contentRevision() != contentRevision) return false;
        bodies.put(id, new CarriageBody<>(contentRevision, current.cells(), Optional.of(baked)));
        return true;
    }

    private void install(UUID id, CarriageBody<B> body) {
        bodies.put(id, body);
        membership.computeIfPresent(id, (key, member) -> new MemberView(member.trainId(), member.index(),
                member.reversed(), Math.max(member.contentRevision(), body.contentRevision())));
    }

    private boolean tracking(UUID id, long seq) {
        return active.contains(id) && trackingSeq.get(id) == seq;
    }

    private GetBody request(UUID id) {
        var body = bodies.get(id);
        return new GetBody(id, trackingSeq.get(id), body == null ? 0 : body.contentRevision());
    }
}
