package willow.train.hexie.compat.create.interaction;

import com.google.common.collect.MapMaker;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.display.PassengerDisplayState;

/** Server-only observations for native Create and snapshot carriages; no timetable execution. */
public final class CreatePassengerDisplaySync {
    private static final Map<CarriageContraptionEntity, PassengerDisplayState> SENT = new MapMaker().weakKeys().makeMap();

    private CreatePassengerDisplaySync() {}

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof CarriageContraptionEntity carriage)
                || carriage.level().isClientSide || carriage.isRemoved() || carriage.tickCount % 20 != 0) return;
        if (!hasSign(carriage)) {
            if (SENT.remove(carriage) != null) HexieBridges.sync().sendPassengerDisplayToTracking(carriage, carriage.getUUID(), PassengerDisplayState.EMPTY);
            return;
        }
        var state = CreatePassengerDisplaySource.read(carriage);
        if (state.equals(SENT.get(carriage))) return;
        SENT.put(carriage, state);
        HexieBridges.sync().sendPassengerDisplayToTracking(carriage, carriage.getUUID(), state);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof CarriageContraptionEntity carriage) || !hasSign(carriage)) return;
        // A one-player refresh must not suppress the next changed broadcast to existing observers.
        HexieBridges.sync().sendPassengerDisplayToPlayer(player, carriage.getUUID(), CreatePassengerDisplaySource.read(carriage));
    }

    static boolean hasSign(CarriageContraptionEntity carriage) {
        return ContraptionInteractionTargets.hasPassengerSigns(carriage.getContraption());
    }
}
