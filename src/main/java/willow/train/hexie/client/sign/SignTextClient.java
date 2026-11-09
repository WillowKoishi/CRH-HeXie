package willow.train.hexie.client.sign;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.compat.iris.client.PassengerPanelTextLayer;

public final class SignTextClient {
    public static void register(IEventBus bus) {
        if (ModList.get().isLoaded("iris")) PassengerPanelTextLayer.register(bus);
        bus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(SignFontProfiles.INSTANCE));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> SignTextRenderer.trim());
    }
    private SignTextClient() {}
}
