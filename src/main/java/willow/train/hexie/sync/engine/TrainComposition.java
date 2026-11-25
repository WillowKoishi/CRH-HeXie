package willow.train.hexie.sync.engine;

import java.util.List;
import java.util.UUID;

public record TrainComposition(UUID trainId, List<UUID> orderedCarriages, List<Boolean> reversed,
                               List<Long> contentRevisions) {
    public TrainComposition {
        orderedCarriages = List.copyOf(orderedCarriages);
        reversed = List.copyOf(reversed);
        contentRevisions = List.copyOf(contentRevisions);
        if (orderedCarriages.size() != reversed.size() || reversed.size() != contentRevisions.size()) {
            throw new IllegalArgumentException("Mismatched carriage columns");
        }
    }
}
