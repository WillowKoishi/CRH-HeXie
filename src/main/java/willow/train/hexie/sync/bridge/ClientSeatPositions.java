package willow.train.hexie.sync.bridge;

import java.util.UUID;
import willow.train.hexie.bridge.sync.ClientSyncBridge;
import willow.train.hexie.sync.cache.ClientVehicleCache;
import willow.train.hexie.sync.s2c.ContraptionSeatPositionCache;

final class ClientSeatPositions implements ClientSyncBridge {
    private final ClientVehicleCache cache;

    ClientSeatPositions(ClientVehicleCache cache) { this.cache = cache; }

    @Override public ClientVehicleCache vehicleCache() { return cache; }

    @Override
    public int get(UUID contraptionId, UUID passengerId, int seatIndex) {
        return ContraptionSeatPositionCache.get(contraptionId, passengerId, seatIndex);
    }

    @Override
    public void clear() {
        ContraptionSeatPositionCache.clear();
        cache.disconnect();
    }

    @Override
    public void clear(UUID contraption) {
        ContraptionSeatPositionCache.clear(contraption);
    }
}
