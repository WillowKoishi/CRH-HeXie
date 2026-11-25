package willow.train.hexie.ui.hud;

import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;

// split from willow/train/hexie/client/events/DrivingConsoleClient.java
public final class DrivingConsoleHudTick {
    private DrivingConsoleHudTick() {}

    public static void tick(DrivingConsoleContext cab) {
        HexieBridges.Client.clientCarriageUi().cabHud().tick(cab);
    }
}
