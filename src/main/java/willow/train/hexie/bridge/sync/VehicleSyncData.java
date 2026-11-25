package willow.train.hexie.bridge.sync;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable fields shared by vehicle producers and the sync protocol. */
public final class VehicleSyncData {
    private VehicleSyncData() {}

    public record Cell(int x, int y, int z) {}

    public record Id(String namespace, String path) {
        public Id {
            Objects.requireNonNull(namespace);
            Objects.requireNonNull(path);
        }
    }

    public record BlockData(Id blockId, Map<String, String> properties, Optional<String> blockEntityData) {
        public BlockData {
            Objects.requireNonNull(blockId);
            properties = Map.copyOf(properties);
            Objects.requireNonNull(blockEntityData);
        }
    }

    /** A saved snapshot can defer converting cells until a tracking client needs geometry. */
    public interface CarriageContent {
        long contentRevision();
        Map<Cell, BlockData> cells();
    }

    public record CarriageRecord(UUID carriageId, long contentRevision, Map<Cell, BlockData> cells) implements CarriageContent {
        public CarriageRecord { cells = Map.copyOf(cells); }
    }

    public interface Membership {
        UUID trainId();
        int index();
        boolean reversed();
        long contentRevision();
    }

    public record TrainRecord(UUID trainId, List<Member> members) {
        public TrainRecord { members = List.copyOf(members); }
        public record Member(UUID carriageId, boolean reversed) {}
    }
}
