package willow.train.hexie.world.bridge;

import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.world.sticker.StickerWorldStore;

/** Install before block, item and block entity suppliers are evaluated. */
public final class WorldBridgeBootstrap {
    private WorldBridgeBootstrap() {}

    public static void install() {
        HexieBridges.registerWorldRegistry(new WorldRegistryPorts());
        HexieBridges.registerWorldBlock(new WorldBlockPorts());
        HexieBridges.registerSticker(new StickerWorldStore());
    }

    public static void installClient() {
        WorldClientPorts.install();
    }
}
