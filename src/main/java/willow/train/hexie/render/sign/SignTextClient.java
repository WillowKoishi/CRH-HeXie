package willow.train.hexie.render.sign;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.bridge.HexieBridges;

public final class SignTextClient {
    public static void register(IEventBus bus) {
        if (ModList.get().isLoaded("iris")) HexieBridges.Client.iris().passengerPanelTextLayer().register(bus);
        bus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(SignFontProfiles.INSTANCE));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> SignTextTextures.trim());
    }
    private SignTextClient() {}
}
