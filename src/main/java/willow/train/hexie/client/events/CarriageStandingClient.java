package willow.train.hexie.client.events;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import willow.train.hexie.Main;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class CarriageStandingClient {
    private static Runnable pendingMovement;
    private static long movementEpoch;

    public static void afterCollisionMovement(Runnable movement) { pendingMovement = movement; }

    public static long movementEpoch() { return movementEpoch; }

    /** A world teleport supersedes the previous standing identity and any queued report. */
    public static void resetMovement(Player player) {
        if (Minecraft.getInstance().player != player) return;
        pendingMovement = null;
        movementEpoch++;
    }

    @SubscribeEvent public static void beforeTick(ClientTickEvent.Pre event) {
        var level = Minecraft.getInstance().level;
        if (level != null) CarriagePlayerSupport.resolveRemote(level);
        CarriagePlayerSupport.beginClientTick();
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void afterTick(ClientTickEvent.Post event) {
        // Create resolves contraption collisions in its normal-priority Post handler.
        // Resolve attachment even when vanilla emitted no packet, then send final feet.
        Runnable movement = pendingMovement;
        pendingMovement = null;
        if (movement != null) movement.run();
    }
    @SubscribeEvent public static void afterPlayer(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) CarriagePlayerSupport.placeRemote(event.getEntity());
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void beforeRender(RenderPlayerEvent.Pre event) {
        var offset = modelOffset(event);
        event.getPoseStack().translate(offset.x, offset.y, offset.z);
    }
    @SubscribeEvent public static void afterRender(RenderPlayerEvent.Post event) {
        var offset = modelOffset(event);
        event.getPoseStack().translate(-offset.x, -offset.y, -offset.z);
    }

    private static Vec3 modelOffset(RenderPlayerEvent event) {
        var player = event.getEntity();
        // LevelRenderer interpolates the model from xOld; Camera interpolates from xo.
        var vanilla = new Vec3(player.xOld, player.yOld, player.zOld).lerp(player.position(), event.getPartialTick());
        if (player.getVehicle() instanceof SnapshotCarriageEntity car) {
            Vec3 seat = car.getPassengerPosition(player, event.getPartialTick());
            if (seat != null) {
                // Reuse seat-specific placement and positionRider's feet offset, at the
                // rendered body origin rather than a straight chord between tick positions.
                Vec3 feet = seat.add(0, SeatEntity.getCustomEntitySeatOffset(player) - .125, 0)
                        .add(car.renderPosition(event.getPartialTick()).subtract(car.position()));
                return feet.subtract(vanilla);
            }
        }
        return CarriagePlayerSupport.renderOffset(player, event.getPartialTick(), vanilla);
    }
}
