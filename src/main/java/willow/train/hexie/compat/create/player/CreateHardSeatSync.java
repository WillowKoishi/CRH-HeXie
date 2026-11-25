package willow.train.hexie.compat.create.player;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import willow.train.hexie.bridge.HexieBridges;

/** Initial observer state complements the existing boarding/dismounting deltas. */
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
        HexieBridges.sync().resetContraptionSeatPosition(player, car.getUUID());
        for (var passenger : car.getPassengers()) {
            var index = car.getContraption().getSeatMapping().get(passenger.getUUID());
            if (index == null) continue;
            int position = HexieBridges.worldBlock().hardSeatPosition().read(passenger.getTags(), index);
            if (position >= 0) HexieBridges.sync().sendContraptionSeatPositionToPlayer(player, car.getUUID(), passenger.getUUID(), index, position);
        }
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof AbstractContraptionEntity car)
            HexieBridges.Client.clientSync().clear(car.getUUID());
    }
}
