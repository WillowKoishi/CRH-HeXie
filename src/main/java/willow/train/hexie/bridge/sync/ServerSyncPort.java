package willow.train.hexie.bridge.sync;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import willow.train.hexie.bridge.sync.RegionData.*;
import willow.train.hexie.bridge.sync.VehicleSyncData.*;

/** Server-thread entry points. Commit body data before publishing membership that references it. */
public interface ServerSyncPort {
    void putBody(CarriageRecord body);
    void compose(Collection<TrainRecord> changed, List<UUID> removed);
    void retire(UUID carriageId);
    void tracking(ServerPlayer player, UUID carriageId, boolean active);

    /** The provider returns the authoritative page, including any edits committed since subscription. */
    void subscribeRegion(ServerPlayer player, RegionKey key, Function<RegionKey, RegionSnapshot> snapshots);
    void unsubscribeRegion(ServerPlayer player, RegionKey key);
    void publishRegions(Collection<RegionPatch> patches);
}
