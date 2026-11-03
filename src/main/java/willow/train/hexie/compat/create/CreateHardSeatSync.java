package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.blocks.seats.HardSeatPosition;
import willow.train.hexie.network.s2c.ContraptionSeatPositionCache;
import willow.train.hexie.network.s2c.ContraptionSeatPositionPacket;

/** Initial observer state complements the existing boarding/dismounting deltas. */
@EventBusSubscriber(modid = "hexie")
public final class CreateHardSeatSync {
    private CreateHardSeatSync() {}

    @SubscribeEvent public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var target = event.getTarget();
        var car = target instanceof AbstractContraptionEntity carriage ? carriage
                : target.getVehicle() instanceof AbstractContraptionEntity vehicle ? vehicle : null;
        if (car != null) sendInitial(player, car);
    }

    private static void sendInitial(ServerPlayer player, AbstractContraptionEntity car) {
        if (car.getContraption() == null) return;
        PacketDistributor.sendToPlayer(player, ContraptionSeatPositionPacket.reset(car.getUUID()));
        for (var passenger : car.getPassengers()) {
            var index = car.getContraption().getSeatMapping().get(passenger.getUUID());
            if (index == null) continue;
            int position = HardSeatPosition.read(passenger.getTags(), index);
            if (position >= 0) PacketDistributor.sendToPlayer(player,
                    new ContraptionSeatPositionPacket(car.getUUID(), passenger.getUUID(), index, position));
        }
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof AbstractContraptionEntity car)
            ContraptionSeatPositionCache.clear(car.getUUID());
    }
}
