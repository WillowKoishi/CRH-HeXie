package willow.train.hexie.bridge.sync;

import willow.train.hexie.bridge.HexieBridges;

/** The sync-owned client port is registered in the existing client service slot. */
public interface ClientSyncBridge extends willow.train.hexie.bridge.ui.client.ClientSyncBridge {
    static ClientSyncBridge current() {
        return (ClientSyncBridge) HexieBridges.Client.clientSync();
    }

    VehicleCacheView vehicleCache();
}
