package willow.train.hexie.ui.events.input;

// split from willow/train/hexie/client/events/DrivingConsoleClient.java

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.ui.hud.DrivingConsoleHudTick;

/** Cab presentation facade. Block-declared input is executed by the shared surface controller. */
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class DrivingConsoleClient {
    private DrivingConsoleClient() {}
    public static DrivingConsoleContext context() { return HexieBridges.Client.surfaceInputClient().surfaceInputClient().cab(); }
    public static DrivingConsoleContext occupiedCab() { return context(); }
    public static DrivingConsoleContext cameraCab() { return HexieBridges.Client.surfaceInputClient().surfaceInputClient().cameraCab(); }

    public static boolean draggingHandle() {
        return DrivingConsoleBindings.draggingHandle();
    }

    public static boolean capturesInput() {
        return DrivingConsoleBindings.capturesInput();
    }

    public static boolean handleKey(int key, int scanCode, int action) {
        return DrivingConsoleBindings.handleKey(key, scanCode, action);
    }

    public static int cabKeyboardMask() {
        return DrivingConsoleBindings.cabKeyboardMask();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        DrivingConsoleHudTick.tick(context());
    }
}
