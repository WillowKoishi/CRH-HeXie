package willow.train.hexie.client.sign;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class SignTextClient {
    public static void register(IEventBus bus) {
        bus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(SignFontProfiles.INSTANCE));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> SignTextRenderer.trim());
    }
    private SignTextClient() {}
}
