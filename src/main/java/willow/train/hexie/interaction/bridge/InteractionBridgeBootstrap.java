package willow.train.hexie.interaction.bridge;

import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.interaction.BlockControlPicking;
import willow.train.hexie.interaction.surface.SurfaceControlPicking;

public final class InteractionBridgeBootstrap {
    private InteractionBridgeBootstrap() {}

    public static void install() {
        HexieBridges.registerInteraction(new InteractionServices());
        HexieBridges.registerItemFactory(new InteractionItemFactory());
        HexieBridges.registerSurfaceInput(new SurfaceInputServices());
        HexieBridges.registerToolOps(new InteractionToolOps());
    }

    public static void installClient() {
        BlockControlPicking.registerClientPicker(SurfaceControlPicking::pick);
        HexieBridges.Client.registerSurfaceInputClient(new ClientSurfaceInputServices());
    }
}
