package willow.train.hexie.ui.bridge;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.ui.sticker.StickerClientController;

/** Composition hooks for the common and physical-client setup phases. */
public final class UiBridgeBootstrap {
    private UiBridgeBootstrap() {}

    public static void install() {
        var menus = new UiMenus();
        HexieBridges.registerUi(() -> menus);
    }

    public static void installClient() {
        HexieBridges.Client.registerClientCarriageUi(new ClientCarriageUi());
        StickerClientController.install();
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        ClientCarriageUi.registerScreens(event);
    }
}
