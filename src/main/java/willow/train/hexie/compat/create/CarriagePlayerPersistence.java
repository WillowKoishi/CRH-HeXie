package willow.train.hexie.compat.create;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import com.simibubi.create.Create;
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
import willow.train.hexie.vehicle.assembly.CarriageSnapshotStore;
import willow.train.hexie.vehicle.player.CarriedPlayerConnection;

/** Server-thread login restoration; the native carriage record remains the entity owner. */
@EventBusSubscriber(modid = "hexie")
public final class CarriagePlayerPersistence {
    private static final String KEY = "HexieFollowingCarriage";
    private static final Map<ServerPlayer, Boolean> PENDING = new WeakHashMap<>();

    static void save(ServerPlayer player) {
        if (PENDING.containsKey(player)) return;
        var car = CarriagePlayerSupport.carriage(player);
        if (car == null) { player.getPersistentData().remove(KEY); return; }
        Vec3 local = CarriagePlayerSupport.localPosition(player);
        var tag = new CompoundTag();
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
            PENDING.remove(player);
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getPersistentData().getCompound(KEY).hasUUID("Carriage")) PENDING.put(player, false);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && PENDING.containsKey(player)) restore(player);
    }

    static void restore(ServerPlayer player) {
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
                level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 3, player.getId());
                nativeCar.manageEntities(level);
                car = level.getEntity(id) instanceof SnapshotCarriageEntity loaded ? loaded : null;
                if (car == null) return;
                break;
            }
            if (car == null) {
                // Legacy non-native entities are restored from their existing entity chunk save.
                var chunk = new ChunkPos(tag.getLong("Chunk"));
                level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunk, 3, player.getId());
                return;
            }
        }
        level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, new ChunkPos(car.blockPosition()), 3, player.getId());
        Vec3 world = CarriagePlayerSupport.frame(car, 1).world(local);
        if (!Boolean.TRUE.equals(PENDING.get(player))) {
            player.teleportTo(level, world.x, world.y, world.z, player.getYRot(), player.getXRot());
            PENDING.put(player, true);
            return;
        }
        if (player.connection instanceof CarriedPlayerConnection connection && !connection.hexie$canCarry()) return;
        Vec3 delta = world.subtract(player.position());
        player.setPos(world);
        if (player.connection instanceof CarriedPlayerConnection connection) connection.hexie$carry(delta);
        CarriagePlayerSupport.accept(player, car, local);
        PacketDistributor.sendToPlayer(player, new CarriagePlayerStatePacket(player.getId(), car.getId(), car.getUUID(), local, true));
        PENDING.remove(player);
        player.getPersistentData().remove(KEY);
    }

    private static void finish(ServerPlayer player) {
        var tag = player.getPersistentData().getCompound(KEY);
        if (PENDING.containsKey(player) && tag.contains("WorldX")) {
            var dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            var level = dimension == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            Vec3 fallback = new Vec3(tag.getDouble("WorldX"), tag.getDouble("WorldY"), tag.getDouble("WorldZ"));
            if (level != null && Double.isFinite(fallback.lengthSqr())
                    && (player.serverLevel() != level || player.position().distanceToSqr(fallback) > 1e-8))
                player.teleportTo(level, fallback.x, fallback.y, fallback.z, player.getYRot(), player.getXRot());
        }
        PENDING.remove(player);
        player.getPersistentData().remove(KEY);
    }
}
