package willow.train.hexie.bridge.ui.client;

import java.util.UUID;

public interface ClientSyncBridge {
    int get(UUID contraptionId, UUID passengerId, int seatIndex);

    void clear();

    void clear(UUID contraption);
}
