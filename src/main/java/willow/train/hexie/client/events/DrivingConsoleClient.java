package willow.train.hexie.client.events;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.client.interaction.SurfaceInputClient;
import willow.train.hexie.vehicle.control.SimpleCabControls;

/** Cab presentation facade. Block-declared input is executed by the shared surface controller. */
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class DrivingConsoleClient {
    private DrivingConsoleClient() {}
    public static DrivingConsoleContext context() { return SurfaceInputClient.cab(); }
    static DrivingConsoleContext occupiedCab() { return context(); }
    static DrivingConsoleContext cameraCab() { return SurfaceInputClient.cameraCab(); }
    static boolean draggingHandle() { return SurfaceInputClient.dragging(); }
    public static boolean capturesInput() { return SurfaceInputClient.capturesInput(); }
    public static boolean handleKey(int key, int scanCode, int action) {
        return SurfaceInputClient.handleKey(key, scanCode, action);
    }
    static int cabKeyboardMask() {
        var held = SurfaceInputClient.heldControls();
        int mask = 0;
        if (held.contains("hexie:forward")) mask |= SimpleCabControls.FORWARD;
        if (held.contains("hexie:backward")) mask |= SimpleCabControls.BACKWARD;
        if (held.contains("hexie:left")) mask |= SimpleCabControls.LEFT;
        if (held.contains("hexie:right")) mask |= SimpleCabControls.RIGHT;
        if (held.contains("hexie:approach")) mask |= SimpleCabControls.APPROACH;
        return mask;
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) { SimpleCabTrainHud.tick(context()); }
}
