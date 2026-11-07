package willow.train.hexie.compat.create;

import java.util.Map;
import java.util.UUID;
import com.google.common.collect.MapMaker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.Main;
import willow.train.hexie.network.s2c.CarriagePlayerStatePacket;

/** Publish resolved carriage-local feet after the authoritative train and player movements. */
@EventBusSubscriber(modid = Main.MOD_ID)
public final class CarriagePlayerSync {
    private static final UUID NO_CARRIAGE = new UUID(0, 0);
    private static final int UPDATE_INTERVAL = 3;
    private record Sent(CarriagePlayerStatePacket packet, boolean settling) {}
    private static final Map<ServerPlayer, Sent> SENT = new MapMaker().weakKeys().makeMap();

    private CarriagePlayerSync() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            var previous = SENT.get(player);
            var car = CarriagePlayerSupport.carriage(player);
            if (car == null && previous == null) continue;

            long now = player.level().getGameTime();
            Vec3 position = car == null ? player.position() : CarriagePlayerSupport.localPosition(player);
            boolean changedCar = previous == null || car == null
                    || previous.packet().carriageId() != car.getId()
                    || !previous.packet().carriageUuid().equals(car.getUUID());
            boolean moved = !changedCar && !previous.packet().local().equals(position);
            if (!changedCar && (now - previous.packet().sampleTick() < UPDATE_INTERVAL
                    || !moved && !previous.settling())) continue;

            var packet = packet(player, car, position, now);
            PacketDistributor.sendToPlayersTrackingEntity(player, packet);
            if (car == null) SENT.remove(player);
            else SENT.put(player, new Sent(packet, moved));
            // A final equal-position sample closes a walk without sending idle traffic forever.
        }
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)) return;
        if (event.getTarget() instanceof ServerPlayer player) {
            var car = CarriagePlayerSupport.carriage(player);
            if (car != null) PacketDistributor.sendToPlayer(observer,
                    packet(player, car, CarriagePlayerSupport.localPosition(player), player.level().getGameTime()));
        } else if (event.getTarget() instanceof SnapshotCarriageEntity car) {
            // The carriage may be paired again while its players remain visible to observers.
            for (ServerPlayer player : observer.serverLevel().players()) {
                if (CarriagePlayerSupport.carriage(player) == car)
                    PacketDistributor.sendToPlayersTrackingEntity(player,
                            packet(player, car, CarriagePlayerSupport.localPosition(player), player.level().getGameTime()));
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SENT.remove(player);
    }

    private static CarriagePlayerStatePacket packet(ServerPlayer player, SnapshotCarriageEntity car,
            Vec3 position, long tick) {
        return new CarriagePlayerStatePacket(player.getId(), car == null ? -1 : car.getId(),
                car == null ? NO_CARRIAGE : car.getUUID(), position, false, tick);
    }
}
