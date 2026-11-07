package willow.train.hexie.compat.create;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.WeakHashMap;
import com.google.common.collect.MapMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import willow.train.hexie.Main;
import willow.train.hexie.vehicle.player.SupportFrame;
import willow.train.hexie.vehicle.player.RemotePlayerInterpolation;

/** One support owner per player; never feeds back into the vehicle's motion solver. */
@EventBusSubscriber(modid = Main.MOD_ID)
public final class CarriagePlayerSupport {
    // Tick/render callbacks run on their owning game thread; packet handlers enqueue
    // there too. Never share players or a lock between the integrated server and client:
    // collision queries can wait for server chunk work. Weak identity keys also keep
    // equal client/server network IDs distinct and allow unloaded players to be collected.
    private static final ThreadLocal<Map<Player, Standing>> STANDING =
            ThreadLocal.withInitial(() -> new MapMaker().weakKeys().makeMap());
    private record ClientRestore(int id, UUID uuid, Vec3 local, long token) {}
    private static final ThreadLocal<Map<Player, ClientRestore>> CLIENT_RESTORE =
            ThreadLocal.withInitial(() -> new MapMaker().weakKeys().makeMap());
    public record ClientPlacementReport(int carriageId, UUID carriageUuid, long token) {}
    private static final ThreadLocal<Map<Player, ClientPlacementReport>> CLIENT_PLACEMENT_REPORT =
            ThreadLocal.withInitial(() -> new MapMaker().weakKeys().makeMap());
    private record RemoteState(int carriageId, UUID carriageUuid, Vec3 local, long tick) {}
    // Pairing a player and pairing its carriage are separate packets. Keep the latest
    // unresolved state until both exist; weak level keys discard it on world changes.
    private static final ThreadLocal<Map<Level, Map<Integer, RemoteState>>> PENDING_REMOTE =
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
        placeStanding(player, car, local);
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(false);
        player.resetFallDistance();
        reportClientPlacement(player, car.getId(), car.getUUID(), restoreToken);
        player.xo = player.xOld = player.getX();
        player.yo = player.yOld = player.getY();
        player.zo = player.zOld = player.getZ();
    }

    public static Vec3 placeStanding(Player player, SnapshotCarriageEntity car, Vec3 local) {
        Standing standing = new Standing(car, local);
        STANDING.get().put(player, standing);
        Vec3 world = standing.frame.world(local);
        // Placement must not be counted again as movement within the carriage.
        placeFromLocal(player, standing, world);
        return world;
    }

    /** Retain the body's lifecycle/visibility until the client supplies its first exit feet. */
    public static void beginDismount(ServerPlayer player, SnapshotCarriageEntity car) {
        STANDING.get().put(player, new Standing(car));
    }

    private static final class Standing {
        final WeakReference<SnapshotCarriageEntity> carriage;
        SupportFrame frame;
        Vec3 local, previousLocal;
        boolean placingFromLocal;
        RemotePlayerInterpolation remote;
        // Identity-only while a server-side seat exit awaits client-resolved coordinates.
        // No frame or position is computed during that handoff.
        Standing(SnapshotCarriageEntity carriage) {
            this.carriage = new WeakReference<>(carriage);
        }
        Standing(SnapshotCarriageEntity carriage, Vec3 local) {
            this(carriage);
            this.frame = frame(carriage, 1);
            this.local = this.previousLocal = local;
        }
    }
    private CarriagePlayerSupport() {}

    public static SupportFrame frame(SnapshotCarriageEntity carriage, float partial) {
        // Actor work poses deliberately omit visual tilting; feet must use the body pose.
        Vec3 origin = carriage.toGlobalVector(Vec3.ZERO, partial);
        Vec3 anchor = carriage.renderPosition(partial);
        origin = origin.add(anchor.subtract(carriage.position()));
        return new SupportFrame(origin, carriage.applyRotation(new Vec3(1, 0, 0), partial),
                carriage.applyRotation(new Vec3(0, 1, 0), partial),
                carriage.applyRotation(new Vec3(0, 0, 1), partial));
    }

    public static SnapshotCarriageEntity carriage(Player player) {
        Standing s = STANDING.get().get(player);
        if (s == null) return null;
        SnapshotCarriageEntity car = s.carriage.get();
        if (car == null || car.isRemoved() || car.level() != player.level() || !eligible(player)
                || (player.level().isClientSide && player.isLocalPlayer() && touchesWorld(player))) {
            STANDING.get().remove(player);
            return null;
        }
        return s.local == null ? null : car;
    }

    private static boolean eligible(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.getAbilities().flying && !player.isFallFlying();
    }

    public static SnapshotCarriageEntity mountedOrStanding(Player player) {
        return player.getVehicle() instanceof SnapshotCarriageEntity mounted ? mounted : carriage(player);
    }

    /** Ownership-only lookup for ticking/tracking decisions; never queries terrain collision. */
    public static SnapshotCarriageEntity attachedCarriage(Player player) {
        if (player.getVehicle() instanceof SnapshotCarriageEntity mounted)
            return mounted.isRemoved() || mounted.level() != player.level() ? null : mounted;
        Standing standing = STANDING.get().get(player);
        SnapshotCarriageEntity car = standing == null ? null : standing.carriage.get();
        return car != null && !car.isRemoved() && car.level() == player.level() && eligible(player) ? car : null;
    }

    public static boolean hasSupportedPlayers(SnapshotCarriageEntity car) {
        if (car.getPassengers().stream().anyMatch(Player.class::isInstance)) return true;
        for (Player player : STANDING.get().keySet())
            if (attachedCarriage(player) == car) return true;
        return false;
    }

    public static boolean supportedBy(Entity entity, SnapshotCarriageEntity car) {
        return entity instanceof Player player && carriage(player) == car;
    }

    /** Called after the body advances, before Create builds its collision candidate list. */
    public static void advance(SnapshotCarriageEntity car) {
        // Only clients simulate standing movement. The server consumes resolved local
        // feet in movement packets and never sweeps/carries players between reports.
        if (!car.level().isClientSide) return;
        SupportFrame next = null;
        // Copy only players supported by this body; entity removal is allowed during callbacks.
        for (Player player : STANDING.get().keySet().toArray(Player[]::new)) {
            Standing candidate = STANDING.get().get(player);
            if (candidate == null || candidate.carriage.get() != car || player.level() != car.level()) continue;
            if (carriage(player) != car) continue;
            Standing s = STANDING.get().get(player);
            if (next == null) next = frame(car, 1);
            if (next.equals(s.frame)) continue;
            SupportFrame previous = s.frame;
            Vec3 carried = next.world(s.local);
            Vec3 displacement = carried.subtract(player.position());
            s.frame = next;
            if (player.level().isClientSide && !player.isLocalPlayer()) {
                placeFromLocal(player, s, carried);
                continue;
            }
            // Sweep against real blocks so a fast carriage cannot carry the player through a wall.
            Vec3 allowed = collideWithLoadedWorld(player, displacement);
            placeFromLocal(player, s, player.position().add(allowed));
            boolean worldHit = allowed.distanceToSqr(displacement) > 1e-12;
            if (worldHit) detach(player);
            else {
                // Vanilla keeps last tick's horizontal walking velocity in world
                // axes. Turn it with the same frame as the feet so a curve does
                // not turn straight-ahead walking into sideways drift. Do not add
                // train speed or rotate vertical jump/gravity velocity.
                player.setDeltaMovement(next.rotateHorizontalFrom(previous, player.getDeltaMovement()));
            }
        }
    }

    public static void contact(SnapshotCarriageEntity car, Entity entity) {
        if (!car.level().isClientSide || !(entity instanceof Player player) || !player.isLocalPlayer() || !eligible(player)
                || touchesWorld(player)) return;
        SnapshotCarriageEntity previous = carriage(player);
        if (previous == car) return;
        // At a gangway both bodies may report contact in the same tick. Keep the
        // current body only while it is still actually touching the player.
        if (previous != null && touchesCarriage(previous, player)) return;
        Standing old = STANDING.get().get(player);
        Standing next = new Standing(car, frame(car, 1).local(player.position()));
        if (previous != null && old != null)
            next.previousLocal = frame(car, 0).local(frame(previous, 0).world(old.previousLocal));
        STANDING.get().put(player, next);

    }

    /** Integrate only the player's own resolved displacement, never the carriage displacement. */
    public static void moved(Player player, Vec3 displacement) {
        if (!player.level().isClientSide || !player.isLocalPlayer()) return;
        Standing s = STANDING.get().get(player);
        if (s == null || s.placingFromLocal || displacement.lengthSqr() == 0) return;
        s.local = s.local.add(new Vec3(displacement.dot(s.frame.x()),
                displacement.dot(s.frame.y()), displacement.dot(s.frame.z())));
    }

    private static void placeFromLocal(Player player, Standing s, Vec3 world) {
        s.placingFromLocal = true;
        try { player.setPos(world); }
        finally { s.placingFromLocal = false; }
    }

    /** Only real world block shapes end attachment; other carriages do not. */
    public static boolean touchesWorld(Player player) {
        Level previous = CarriageChunkAccess.enter(player);
        try {
            return player.level().getBlockCollisions(player, player.getBoundingBox().inflate(1e-5))
                    .iterator().hasNext();
        } finally { CarriageChunkAccess.exit(previous); }
    }

    private static Vec3 collideWithLoadedWorld(Player player, Vec3 displacement) {
        Level previous = CarriageChunkAccess.enter(player);
        try {
            return Entity.collideBoundingBox(player, displacement, player.getBoundingBox(),
                    player.level(), java.util.List.of());
        } finally { CarriageChunkAccess.exit(previous); }
    }

    public static boolean touchesCarriage(SnapshotCarriageEntity car, Player player) {
        return touchesCarriage(car, player.getBoundingBox(), frame(car, 1));
    }

    private static boolean touchesCarriage(SnapshotCarriageEntity car, net.minecraft.world.phys.AABB bounds,
                                          SupportFrame frame) {
        if (car.getContraption() == null) return false;
        var box = bounds.inflate(.05);
        net.minecraft.world.phys.AABB local = null;
        for (double x : new double[] {box.minX, box.maxX})
            for (double y : new double[] {box.minY, box.maxY})
                for (double z : new double[] {box.minZ, box.maxZ}) {
                    Vec3 point = frame.local(new Vec3(x, y, z));
                    var corner = new net.minecraft.world.phys.AABB(point, point);
                    local = local == null ? corner : local.minmax(corner);
                }
        var world = car.getContraption().getContraptionWorld();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(local.minX, local.minY, local.minZ),
                BlockPos.containing(local.maxX, local.maxY, local.maxZ))) {
            if (car.getContraption().isHiddenInPortal(pos)) continue;
            for (var shape : world.getBlockState(pos).getCollisionShape(world, pos).toAabbs())
                if (shape.move(pos).intersects(local)) return true;
        }
        return false;
    }

    /** Feet-only probes in the cached contraption world, not its overall bounding box. */
    public static boolean hasFloor(SnapshotCarriageEntity car, Vec3 feet, float width) {
        if (car.getContraption() == null) return false;
        var world = car.getContraption().getContraptionWorld();
        SupportFrame frame = frame(car, 1);
        double radius = Math.max(0, width * .5 - .015);
        for (int i = 0; i < 5; i++) {
            double x = i == 0 ? 0 : (i <= 2 ? -radius : radius);
            double z = i == 0 ? 0 : (i % 2 == 0 ? -radius : radius);
            Vec3 start = frame.local(feet.add(x, .08, z));
            Vec3 end = frame.local(feet.add(x, -.16, z));
            BlockPos min = BlockPos.containing(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z));
            BlockPos max = BlockPos.containing(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z));
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (car.getContraption().isHiddenInPortal(pos)) continue;
                var state = world.getBlockState(pos);
                var hit = state.getCollisionShape(world, pos).clip(start, end, pos);
                if (hit != null && !hit.isInside()) {
                    Vec3 normal = car.applyRotation(Vec3.atLowerCornerOf(hit.getDirection().getNormal()), 1);
                    if (normal.y > .5) return true;
                }
            }
        }
        return false;
    }

    public static void detach(Player player) {
        STANDING.get().remove(player);
    }

    /** A real world teleport supersedes both standing and delayed seat/login restoration. */
    public static void worldTeleport(Player player) {
        detach(player);
        CLIENT_RESTORE.get().remove(player);
        CLIENT_PLACEMENT_REPORT.get().remove(player);
    }

    public static Vec3 localPosition(Player player) {
        // Callers already resolved the support owner. Do not repeat the client's
        // world-contact query just to read the coordinate from that same record.
        Standing standing = STANDING.get().get(player);
        return standing == null || standing.local == null ? player.position() : standing.local;
    }

    public static void accept(Player player, SnapshotCarriageEntity car, Vec3 local) {
        Standing old = STANDING.get().get(player);
        if (old == null || old.local == null || old.carriage.get() != car) STANDING.get().put(player, new Standing(car, local));
        else { old.previousLocal = old.local; old.local = local; old.frame = frame(car, 1); }

    }

    public static Vec3 renderOffset(Player player, float partial) {
        return renderOffset(player, partial, player.getPosition(partial));
    }

    public static Vec3 renderOffset(Player player, float partial, Vec3 vanillaPosition) {
        SnapshotCarriageEntity car = carriage(player);
        if (car == null) return Vec3.ZERO;
        Standing s = STANDING.get().get(player);
        Vec3 local = s.previousLocal.lerp(s.local, partial);
        Vec3 expected = frame(car, partial).world(local);
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

    private static boolean applyRemote(Level level, int playerId, RemoteState state) {
        if (!(level.getEntity(playerId) instanceof Player player)) return false;
        if (player.isLocalPlayer()) return true;
        if (state.carriageId < 0) {
            detach(player);
            if (!player.isPassenger())
                player.lerpTo(state.local.x, state.local.y, state.local.z,
                        player.lerpTargetYRot(), player.lerpTargetXRot(), 3);
            return true;
        }
        if (player.isPassenger()) {
            detach(player);
            // The standing snapshot can precede vanilla's passenger-list update.
            return false;
        }
        if (!(level.getEntity(state.carriageId) instanceof SnapshotCarriageEntity car)
                || !car.getUUID().equals(state.carriageUuid)) return false;
        acceptRemote(player, car, state.local, state.tick);
        return true;
    }

    public static void acceptRemote(Player player, SnapshotCarriageEntity car, Vec3 local, long sampleTick) {
        SnapshotCarriageEntity previous = carriage(player);
        Standing standing = STANDING.get().get(player);
        if (previous != car) {
            Vec3 start = previous == null ? local
                    : frame(car, 1).local(frame(previous, 1).world(standing.local));
            standing = new Standing(car, start);
            standing.remote = new RemotePlayerInterpolation(start, sampleTick - (previous == null ? 0 : 3));
            STANDING.get().put(player, standing);
            placeFromLocal(player, standing, standing.frame.world(start));
        }
        if (standing.remote == null) standing.remote = new RemotePlayerInterpolation(standing.local, sampleTick);
        standing.remote.accept(local, sampleTick);
    }

    public static Vec3 remoteMovement(Player player) {
        Standing standing = STANDING.get().get(player);
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
        for (Player player : STANDING.get().keySet().toArray(Player[]::new)) {
            if (!player.level().isClientSide || carriage(player) == null) continue;
            beginClientTick(player, player.isLocalPlayer());
        }
    }

    static void beginClientTick(Player player, boolean localPlayer) {
        Standing s = STANDING.get().get(player);
        if (s == null) return;
        // Local movement is already integrated when the player moves, including collision response.
        s.previousLocal = s.local;
        if (!localPlayer && s.remote != null) s.local = s.remote.tick();
    }

    public static void placeRemote(Player player) {
        var car = carriage(player);
        if (car != null && !player.isLocalPlayer()) {
            Standing standing = STANDING.get().get(player);
            standing.frame = frame(car, 1);
            placeFromLocal(player, standing, standing.frame.world(standing.local));
        }
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            STANDING.get().remove(player);
            CLIENT_RESTORE.get().remove(player);
            CLIENT_PLACEMENT_REPORT.get().remove(player);
            var pending = PENDING_REMOTE.get().get(player.level());
            if (pending != null) pending.remove(player.getId());
        }
        if (event.getEntity() instanceof SnapshotCarriageEntity car) {
            STANDING.get().entrySet().removeIf(e -> e.getValue().carriage.get() == car);
            if (car.level().isClientSide) CLIENT_RESTORE.get().entrySet().removeIf(entry -> {
                ClientRestore restore = entry.getValue();
                if (restore.id() != car.getId() || !restore.uuid().equals(car.getUUID())) return false;
                // The target disappeared before placement. Release the same handoff
                // with the client's current world feet instead of waiting forever.
                Player player = entry.getKey();
                if (!player.isRemoved() && player.isLocalPlayer())
                    reportClientPlacement(player, restore.id(), restore.uuid(), restore.token());
                return true;
            });
        }
    }
}
