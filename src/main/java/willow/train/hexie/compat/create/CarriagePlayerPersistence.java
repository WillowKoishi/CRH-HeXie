package willow.train.hexie.compat.create;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.sync.ContraptionSeatMappingPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.network.s2c.CarriagePlayerStatePacket;
import willow.train.hexie.network.s2c.ContraptionSeatPositionPacket;
import willow.train.hexie.blocks.seats.HardSeatPositionRequest;
import willow.train.hexie.blocks.seats.HardSeatPosition;
import willow.train.hexie.vehicle.assembly.CarriageSnapshotStore;
import willow.train.hexie.vehicle.player.CarriedPlayerConnection;

/** Server-thread login restoration; the native carriage record remains the entity owner. */
@EventBusSubscriber(modid = "hexie")
public final class CarriagePlayerPersistence {
    private static final String KEY = "HexieFollowingCarriage";
    private static final Map<ServerPlayer, Boolean> PENDING = new WeakHashMap<>();
    private static final java.util.Set<ServerPlayer> RESTORE_TELEPORTS =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<ServerPlayer, RestoreTicket> TICKETS = new WeakHashMap<>();
    private record RestoreTicket(net.minecraft.server.level.ServerLevel level, ChunkPos chunk) {}

    private static final Map<ServerPlayer, WeakReference<SnapshotCarriageEntity>> TRACKED_RESTORE_CAR = new WeakHashMap<>();

    @SubscribeEvent public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof SnapshotCarriageEntity car)) return;
        var tag = player.getPersistentData().getCompound(KEY);
        if (tag.contains("Seat") && tag.hasUUID("Carriage") && tag.getUUID("Carriage").equals(car.getUUID())) {
            // ServerEntity fires this only after sending the entity's pairing bundle.
            // This can precede PlayerLoggedInEvent, so use the loaded save record here.
            TRACKED_RESTORE_CAR.put(player, new WeakReference<>(car));
        }
    }

    @SubscribeEvent public static void stopTracking(PlayerEvent.StopTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var tracked = TRACKED_RESTORE_CAR.get(player);
        if (tracked != null && tracked.get() == event.getTarget()) TRACKED_RESTORE_CAR.remove(player);
    }

    static void save(ServerPlayer player) {
        if (PENDING.containsKey(player)) return;
        var car = player.getVehicle() instanceof SnapshotCarriageEntity mounted
                ? mounted : CarriagePlayerSupport.carriage(player);
        if (car == null) { player.getPersistentData().remove(KEY); return; }
        BlockPos seat = player.getVehicle() == car
                ? car.getContraption().getSeatOf(player.getUUID()) : null;
        Vec3 local = seat == null ? CarriagePlayerSupport.localPosition(player)
                : CarriagePlayerSupport.frame(car, 1).local(player.position());
        var tag = new CompoundTag();
        if (seat != null) {
            tag.putLong("Seat", seat.asLong());
            int seatIndex = car.getContraption().getSeats().indexOf(seat);
            int position = HardSeatPosition.read(player.getTags(), seatIndex);
            if (position >= 0) tag.putInt("SeatPosition", position);
        }
        tag.putUUID("Carriage", car.getUUID());
        tag.putString("Dimension", car.level().dimension().location().toString());
        tag.putDouble("X", local.x); tag.putDouble("Y", local.y); tag.putDouble("Z", local.z);
        tag.putLong("Chunk", new ChunkPos(car.blockPosition()).toLong());
        tag.putDouble("WorldX", player.getX()); tag.putDouble("WorldY", player.getY()); tag.putDouble("WorldZ", player.getZ());
        player.getPersistentData().put(KEY, tag);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // PlayerList fires this before saving the player and removing its entity.
            save(player);
            forgetSession(player);
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getPersistentData().getCompound(KEY).hasUUID("Carriage")) PENDING.put(player, false);
    }

    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PENDING.containsKey(player)
                && !RESTORE_TELEPORTS.contains(player)) cancel(player);
    }

    @SubscribeEvent public static void died(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player && PENDING.containsKey(player))
            cancel(player);
    }

    @SubscribeEvent public static void cloned(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer old && PENDING.containsKey(old)) {
            cancel(old);
            event.getEntity().getPersistentData().remove(KEY);
        }
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && PENDING.containsKey(player)) restore(player);
    }

    static void restore(ServerPlayer player) {
        if (player.isDeadOrDying() || player.isRemoved()) { cancel(player); return; }
        var tag = player.getPersistentData().getCompound(KEY);
        if (!tag.hasUUID("Carriage")) { finish(player); return; }
        var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        var level = dimension == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        Vec3 local = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        if (level == null || !Double.isFinite(local.lengthSqr())) { finish(player); return; }
        UUID id = tag.getUUID("Carriage");
        SnapshotCarriageEntity car = level.getEntity(id) instanceof SnapshotCarriageEntity found ? found : null;
        if (car == null) {
            if (CarriageSnapshotStore.get(level).find(id).isEmpty()) { finish(player); return; }
            // Membership can change after coupling. Resolve by carriage UUID, never train index.
            for (var value : java.util.List.copyOf(Create.RAILWAYS.trains.values())) {
                if (!(value instanceof CompatibilityTrain train) || train.invalid) continue;
                int index = train.carriageIds.indexOf(id);
                if (index < 0) continue;
                var nativeCar = train.carriages.get(index);
                var dimensional = nativeCar.getDimensionalIfPresent(level.dimension());
                if (dimensional == null || dimensional.positionAnchor == null) return;
                var chunk = new ChunkPos(BlockPos.containing(dimensional.positionAnchor));
                keepTicket(player, level, chunk);
                nativeCar.manageEntities(level);
                car = level.getEntity(id) instanceof SnapshotCarriageEntity loaded ? loaded : null;
                if (car == null) return;
                break;
            }
            if (car == null) {
                // Legacy non-native entities are restored from their existing entity chunk save.
                var chunk = new ChunkPos(tag.getLong("Chunk"));
                keepTicket(player, level, chunk);
                return;
            }
        }
        keepTicket(player, level, new ChunkPos(car.blockPosition()));
        Vec3 world = CarriagePlayerSupport.frame(car, 1).world(local);
        if (!Boolean.TRUE.equals(PENDING.get(player))) {
            teleport(player, level, world);
            PENDING.put(player, true);
            return;
        }
        if (player.connection instanceof CarriedPlayerConnection connection && !connection.hexie$canCarry()) return;
        if (tag.contains("Seat")) {
            var tracked = TRACKED_RESTORE_CAR.get(player);
            if (tracked == null || tracked.get() != car) return;
            BlockPos seat = BlockPos.of(tag.getLong("Seat"));
            int seatIndex = car.getContraption().getSeats().indexOf(seat);
            if (seatIndex >= 0) {
                String request = tag.contains("SeatPosition")
                        ? HardSeatPositionRequest.tag(seatIndex, tag.getInt("SeatPosition")) : null;
                if (request != null) player.addTag(request);
                try {
                    if (player.getVehicle() != car
                            || !seat.equals(car.getContraption().getSeatOf(player.getUUID())))
                        car.addSittingPassenger(player, seatIndex);
                } finally {
                    if (request != null) player.removeTag(request);
                }
                if (player.getVehicle() == car
                        && seat.equals(car.getContraption().getSeatOf(player.getUUID()))) {
                    // Pairing data is already queued on this connection. Send the complete
                    // restored seat state even when the player was already mounted.
                    PacketDistributor.sendToPlayer(player,
                            new ContraptionSeatMappingPacket(car.getId(), car.getContraption().getSeatMapping()));
                    int position = HardSeatPosition.read(player.getTags(), seatIndex);
                    if (position >= 0) PacketDistributor.sendToPlayer(player, new ContraptionSeatPositionPacket(
                            car.getUUID(), player.getUUID(), seatIndex, position));
                    // Vanilla sends the changed passenger list; if already mounted, it
                    // was included in pairing. Do not resend it: the client ejects its
                    // passengers before applying that list, clearing hard-seat metadata.
                    CarriagePlayerSupport.detach(player);
                    cancel(player);
                    return;
                }
            }
        }
        Vec3 delta = world.subtract(player.position());
        player.setPos(world);
        if (player.connection instanceof CarriedPlayerConnection connection) connection.hexie$carry(delta);
        CarriagePlayerSupport.accept(player, car, local);
        PacketDistributor.sendToPlayer(player, new CarriagePlayerStatePacket(player.getId(), car.getId(), car.getUUID(), local, true));
        cancel(player);
    }

    private static void finish(ServerPlayer player) {
        var tag = player.getPersistentData().getCompound(KEY);
        if (PENDING.containsKey(player) && tag.contains("WorldX")) {
            var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            var level = dimension == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            Vec3 fallback = new Vec3(tag.getDouble("WorldX"), tag.getDouble("WorldY"), tag.getDouble("WorldZ"));
            if (level != null && Double.isFinite(fallback.lengthSqr())
                    && (player.serverLevel() != level || player.position().distanceToSqr(fallback) > 1e-8))
                teleport(player, level, fallback);
        }
        cancel(player);
    }
    private static void keepTicket(ServerPlayer player, net.minecraft.server.level.ServerLevel level, ChunkPos chunk) {
        var previous = TICKETS.put(player, new RestoreTicket(level, chunk));
        if (previous != null && (previous.level() != level || !previous.chunk().equals(chunk)))
            previous.level().getChunkSource().removeRegionTicket(TicketType.POST_TELEPORT, previous.chunk(), 3, player.getId());
        level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 3, player.getId());
    }

    private static void teleport(ServerPlayer player, net.minecraft.server.level.ServerLevel level, Vec3 position) {
        RESTORE_TELEPORTS.add(player);
        try { player.teleportTo(level, position.x, position.y, position.z, player.getYRot(), player.getXRot()); }
        finally { RESTORE_TELEPORTS.remove(player); }
    }

    private static void forgetSession(ServerPlayer player) {
        PENDING.remove(player);
        TRACKED_RESTORE_CAR.remove(player);
        var ticket = TICKETS.remove(player);
        if (ticket != null) ticket.level().getChunkSource().removeRegionTicket(
                TicketType.POST_TELEPORT, ticket.chunk(), 3, player.getId());
    }

    private static void cancel(ServerPlayer player) {
        forgetSession(player);
        player.getPersistentData().remove(KEY);
    }

}
