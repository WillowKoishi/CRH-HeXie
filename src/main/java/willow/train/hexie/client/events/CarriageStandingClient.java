package willow.train.hexie.client.events;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import willow.train.hexie.Main;
import willow.train.hexie.compat.create.CarriagePlayerSupport;

@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class CarriageStandingClient {
    private static Runnable pendingMovement;

    public static void afterCollisionMovement(Runnable movement) { pendingMovement = movement; }

    @SubscribeEvent public static void beforeTick(ClientTickEvent.Pre event) { CarriagePlayerSupport.beginClientTick(); }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void afterTick(ClientTickEvent.Post event) {
        // Create resolves contraption collisions in its normal-priority Post handler.
        // Send the final feet coordinate, not the pre-collision position from Player.tick().
        Runnable movement = pendingMovement;
        pendingMovement = null;
        if (movement != null) movement.run();
    }
    @SubscribeEvent public static void afterPlayer(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) CarriagePlayerSupport.placeRemote(event.getEntity());
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void beforeRender(RenderPlayerEvent.Pre event) {
        var offset = CarriagePlayerSupport.renderOffset(event.getEntity(), event.getPartialTick());
        event.getPoseStack().translate(offset.x, offset.y, offset.z);
    }
    @SubscribeEvent public static void afterRender(RenderPlayerEvent.Post event) {
        var offset = CarriagePlayerSupport.renderOffset(event.getEntity(), event.getPartialTick());
        event.getPoseStack().translate(-offset.x, -offset.y, -offset.z);
    }
}
