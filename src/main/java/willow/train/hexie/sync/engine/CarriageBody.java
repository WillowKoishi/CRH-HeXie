package willow.train.hexie.sync.engine;

import java.util.Map;
import java.util.Optional;
import willow.train.hexie.bridge.sync.VehicleSyncData.Cell;
import willow.train.hexie.bridge.sync.VehicleSyncData.CarriageContent;
import willow.train.hexie.bridge.sync.VehicleSyncData.BlockData;

/** B is the adapter's compiled mesh, collision and control indexes for this exact revision. */
public record CarriageBody<B>(long contentRevision, Map<Cell, BlockData> cells, Optional<B> baked) implements CarriageContent {
    public CarriageBody {
        cells = Map.copyOf(cells);
    }
}
