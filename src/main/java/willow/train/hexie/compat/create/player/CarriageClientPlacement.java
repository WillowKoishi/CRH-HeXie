package willow.train.hexie.compat.create.player;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.WeakHashMap;
import com.google.common.collect.MapMaker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

// split from CarriagePlayerSupport.java
public final class CarriageClientPlacement {
    private CarriageClientPlacement() {}

    record ClientRestore(int id, UUID uuid, Vec3 local, long token) {}
    static final ThreadLocal<Map<Player, ClientRestore>> CLIENT_RESTORE =
            ThreadLocal.withInitial(() -> new MapMaker().weakKeys().makeMap());
    public record ClientPlacementReport(int carriageId, UUID carriageUuid, long token) {}
    static final ThreadLocal<Map<Player, ClientPlacementReport>> CLIENT_PLACEMENT_REPORT =
            ThreadLocal.withInitial(() -> new MapMaker().weakKeys().makeMap());
    private record RemoteState(int carriageId, UUID carriageUuid, Vec3 local, long tick) {}
    // Pairing a player and pairing its carriage are separate packets. Keep the latest
    // unresolved state until both exist; weak level keys discard it on world changes.
    static final ThreadLocal<Map<Level, Map<Integer, RemoteState>>> PENDING_REMOTE =
            ThreadLocal.withInitial(WeakHashMap::new);

    public static void queueClientRestore(Player player, int id, java.util.UUID uuid, Vec3 local, long token) {
        CLIENT_RESTORE.get().put(player, new ClientRestore(id, uuid, local, token));
    }

    /** Finish the handoff even when contact, flight or carrier removal immediately releases it. */
    public static ClientPlacementReport takeClientPlacementReport(Player player) {
        return CLIENT_PLACEMENT_REPORT.get().remove(player);
    }

    public static void reportClientPlacement(Player player, int carriageId, UUID carriageUuid, long token) {
        CLIENT_PLACEMENT_REPORT.get().put(player, new ClientPlacementReport(carriageId, carriageUuid, token));
    }

    /** The local client owns the exit feet and starts following at the body's current pose. */
    public static void startClientFollowing(Player player, SnapshotCarriageEntity car, Vec3 local, long restoreToken) {
        player.getPersistentData().remove("ContraptionDismountLocation");
        CarriagePlayerSupport.placeStanding(player, car, local);
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(false);
        player.resetFallDistance();
        reportClientPlacement(player, car.getId(), car.getUUID(), restoreToken);
        player.xo = player.xOld = player.getX();
        player.yo = player.yOld = player.getY();
        player.zo = player.zOld = player.getZ();
    }

    public static Vec3 renderOffset(Player player, float partial) {
        return renderOffset(player, partial, player.getPosition(partial));
    }

    public static Vec3 renderOffset(Player player, float partial, Vec3 vanillaPosition) {
        SnapshotCarriageEntity car = CarriagePlayerSupport.carriage(player);
        if (car == null) return Vec3.ZERO;
        CarriagePlayerSupport.Standing s = CarriagePlayerSupport.STANDING.get().get(player);
        Vec3 local = s.previousLocal.lerp(s.local, partial);
        Vec3 expected = CarriagePlayerSupport.frame(car, partial).world(local);
        return expected.subtract(vanillaPosition);
    }

    public static void receiveRemote(Level level, int playerId, int carriageId, UUID carriageUuid,
            Vec3 local, long sampleTick) {
        var state = new RemoteState(carriageId, carriageUuid, local, sampleTick);
        var pending = PENDING_REMOTE.get().computeIfAbsent(level, ignored -> new HashMap<>());
        if (applyRemote(level, playerId, state)) pending.remove(playerId);
        else pending.put(playerId, state);
    }

    public static void resolveRemote(Level level) {
        var pending = PENDING_REMOTE.get().get(level);
        if (pending != null) pending.entrySet().removeIf(entry -> applyRemote(level, entry.getKey(), entry.getValue()));
    }

    public static boolean applyRemote(Level level, int playerId, RemoteState state) {
        if (!(level.getEntity(playerId) instanceof Player player)) return false;
        if (player.isLocalPlayer()) return true;
        if (state.carriageId < 0) {
            CarriagePlayerSupport.detach(player);
            if (!player.isPassenger())
                player.lerpTo(state.local.x, state.local.y, state.local.z,
                        player.lerpTargetYRot(), player.lerpTargetXRot(), 3);
            return true;
        }
        if (player.isPassenger()) {
            CarriagePlayerSupport.detach(player);
            // The standing snapshot can precede vanilla's passenger-list update.
            return false;
        }
        if (!(level.getEntity(state.carriageId) instanceof SnapshotCarriageEntity car)
                || !car.getUUID().equals(state.carriageUuid)) return false;
        acceptRemote(player, car, state.local, state.tick);
        return true;
    }

    public static void acceptRemote(Player player, SnapshotCarriageEntity car, Vec3 local, long sampleTick) {
        SnapshotCarriageEntity previous = CarriagePlayerSupport.carriage(player);
        CarriagePlayerSupport.Standing standing = CarriagePlayerSupport.STANDING.get().get(player);
        if (previous != car) {
            Vec3 start = previous == null ? local
                    : CarriagePlayerSupport.frame(car, 1).local(CarriagePlayerSupport.frame(previous, 1).world(standing.local));
            standing = new CarriagePlayerSupport.Standing(car, start);
            standing.remote = HexieBridges.playerCarriage().createRemotePlayerInterpolation(start, sampleTick - (previous == null ? 0 : 3));
            CarriagePlayerSupport.STANDING.get().put(player, standing);
            CarriagePlayerSupport.placeFromLocal(player, standing, standing.frame.world(start));
        }
        if (standing.remote == null) standing.remote = HexieBridges.playerCarriage().createRemotePlayerInterpolation(standing.local, sampleTick);
        standing.remote.accept(local, sampleTick);
    }

    public static Vec3 remoteMovement(Player player) {
        CarriagePlayerSupport.Standing standing = CarriagePlayerSupport.STANDING.get().get(player);
        return standing == null || standing.local == null ? Vec3.ZERO : standing.local.subtract(standing.previousLocal);
    }

    public static void beginClientTick() {
        for (Player player : CLIENT_RESTORE.get().keySet().toArray(Player[]::new)) {
            var restore = CLIENT_RESTORE.get().get(player);
            if (player.isRemoved()) { CLIENT_RESTORE.get().remove(player); continue; }
            // The native passenger-list packet may arrive after the local placement.
            // A later successful mount sends a world teleport, which cancels this queue.
            if (player.isPassenger()) continue;
            if (player.level().getEntity(restore.id()) instanceof SnapshotCarriageEntity car
                    && car.getUUID().equals(restore.uuid())) {
                // Even immediate world contact or flight must finish the handoff
                // before sending its single world-space exit report.
                startClientFollowing(player, car, restore.local(), restore.token());
                CLIENT_RESTORE.get().remove(player);
            }
        }
        for (Player player : CarriagePlayerSupport.STANDING.get().keySet().toArray(Player[]::new)) {
            if (!player.level().isClientSide || CarriagePlayerSupport.carriage(player) == null) continue;
            beginClientTick(player, player.isLocalPlayer());
        }
    }

    public static void beginClientTick(Player player, boolean localPlayer) {
        CarriagePlayerSupport.Standing s = CarriagePlayerSupport.STANDING.get().get(player);
        if (s == null) return;
        // Local movement is already integrated when the player moves, including collision response.
        s.previousLocal = s.local;
        if (!localPlayer && s.remote != null) s.local = s.remote.tick();
    }

    public static void placeRemote(Player player) {
        var car = CarriagePlayerSupport.carriage(player);
        if (car != null && !player.isLocalPlayer()) {
            CarriagePlayerSupport.Standing standing = CarriagePlayerSupport.STANDING.get().get(player);
            standing.frame = CarriagePlayerSupport.frame(car, 1);
            CarriagePlayerSupport.placeFromLocal(player, standing, standing.frame.world(standing.local));
        }
    }
}
