package willow.train.hexie.render.bridge;

import willow.train.hexie.bridge.HexieBridges;

/** Install before Create constructs client renderers for the registered bogey styles. */
public final class RenderBridgeBootstrap {
    private RenderBridgeBootstrap() {}

    public static void installClient() {
        HexieBridges.Client.registerRender(new RenderServices());
    }
}
