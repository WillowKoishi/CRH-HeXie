package willow.train.hexie.sync.engine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import willow.train.hexie.bridge.sync.VehicleSyncData.Cell;
import willow.train.hexie.bridge.sync.VehicleSyncData.BlockData;

public sealed interface SyncMessage {
    record Interest(UUID carriageId, long interestSeq, boolean tracking) implements SyncMessage {}

    /** Each changed train is its complete new membership. removed contains train IDs. */
    record Composition(long compositionRevision, List<TrainComposition> changed,
                       List<UUID> removed) implements SyncMessage {
        public Composition {
            changed = List.copyOf(changed);
            removed = List.copyOf(removed);
        }
    }

    /** Zero also denotes an empty cache: revision-zero bodies must be returned for such a request. */
    record GetBody(UUID carriageId, long interestSeq, long haveContentRevision) implements SyncMessage {}
    record Body(UUID carriageId, long interestSeq, long contentRevision, byte[] cells) implements SyncMessage {
        public Body { cells = cells.clone(); }
        @Override public byte[] cells() { return cells.clone(); }
    }

    record CellChange(Cell cell, Optional<BlockData> block) {}
    record CellPatch(UUID carriageId, long interestSeq, long baseRevision,
                     long newRevision, List<CellChange> changes) implements SyncMessage {
        public CellPatch {
            if (newRevision <= baseRevision) throw new IllegalArgumentException("Content revision must advance");
            changes = List.copyOf(changes);
        }
    }
}
