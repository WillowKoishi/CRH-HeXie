package willow.train.hexie.client.interaction;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/** Direct mouse use shares control operations without entering captured input mode. */
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class WorldBlockControls {
    private WorldBlockControls() {}
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        SurfaceInputClient.directClick(event);
    }
}
