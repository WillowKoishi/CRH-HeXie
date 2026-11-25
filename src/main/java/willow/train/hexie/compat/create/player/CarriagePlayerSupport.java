package willow.train.hexie.compat.create.player;

import java.lang.ref.WeakReference;
import java.util.Map;
import com.google.common.collect.MapMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import java.util.UUID;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.PlayerCarriageBridge.RemotePlayerInterpolationView;
import willow.train.hexie.bridge.vehicle.PlayerCarriageBridge.SupportFrameView;
import willow.train.hexie.compat.create.motion.CarriageChunkAccess;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

/** One support owner per player; never feeds back into the vehicle's motion solver. */
public final class CarriagePlayerSupport {
    public static void queueClientRestore(Player player, int id, java.util.UUID uuid, Vec3 local, long token) {
        CarriageClientPlacement.queueClientRestore(player, id, uuid, local, token);
    }

    public static CarriageClientPlacement.ClientPlacementReport takeClientPlacementReport(Player player) {
        return CarriageClientPlacement.takeClientPlacementReport(player);
    }

    public static void reportClientPlacement(Player player, int carriageId, UUID carriageUuid, long token) {
        CarriageClientPlacement.reportClientPlacement(player, carriageId, carriageUuid, token);
    }

    public static void startClientFollowing(Player player, SnapshotCarriageEntity car, Vec3 local, long restoreToken) {
        CarriageClientPlacement.startClientFollowing(player, car, local, restoreToken);
    }

    public static Vec3 renderOffset(Player player, float partial) {
        return CarriageClientPlacement.renderOffset(player, partial);
    }

    public static Vec3 renderOffset(Player player, float partial, Vec3 vanillaPosition) {
        return CarriageClientPlacement.renderOffset(player, partial, vanillaPosition);
    }

    public static void receiveRemote(Level level, int playerId, int carriageId, UUID carriageUuid,
            Vec3 local, long sampleTick) {
        CarriageClientPlacement.receiveRemote(level, playerId, carriageId, carriageUuid, local, sampleTick);
    }

    public static void resolveRemote(Level level) {
        CarriageClientPlacement.resolveRemote(level);
    }

    public static void acceptRemote(Player player, SnapshotCarriageEntity car, Vec3 local, long sampleTick) {
        CarriageClientPlacement.acceptRemote(player, car, local, sampleTick);
    }

    public static Vec3 remoteMovement(Player player) {
        return CarriageClientPlacement.remoteMovement(player);
    }

    public static void beginClientTick() {
        CarriageClientPlacement.beginClientTick();
    }

    static void beginClientTick(Player player, boolean localPlayer) {
        CarriageClientPlacement.beginClientTick(player, localPlayer);
    }

    public static void placeRemote(Player player) {
        CarriageClientPlacement.placeRemote(player);
    }

    // Tick/render callbacks run on their owning game thread; packet handlers enqueue
    // there too. Never share players or a lock between the integrated server and client:
    // collision queries can wait for server chunk work. Weak identity keys also keep
    // equal client/server network IDs distinct and allow unloaded players to be collected.
    static final ThreadLocal<Map<Player, Standing>> STANDING =
            ThreadLocal.withInitial(() -> new MapMaker().weakKeys().makeMap());

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

    static final class Standing {
        final WeakReference<SnapshotCarriageEntity> carriage;
        SupportFrameView frame;
        Vec3 local, previousLocal;
        boolean placingFromLocal;
        RemotePlayerInterpolationView remote;
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

    public static SupportFrameView frame(SnapshotCarriageEntity carriage, float partial) {
        // Actor work poses deliberately omit visual tilting; feet must use the body pose.
        Vec3 origin = carriage.toGlobalVector(Vec3.ZERO, partial);
        Vec3 anchor = carriage.renderPosition(partial);
        origin = origin.add(anchor.subtract(carriage.position()));
        return HexieBridges.playerCarriage().createSupportFrame(origin, carriage.applyRotation(new Vec3(1, 0, 0), partial),
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
        SupportFrameView next = null;
        // Copy only players supported by this body; entity removal is allowed during callbacks.
        for (Player player : STANDING.get().keySet().toArray(Player[]::new)) {
            Standing candidate = STANDING.get().get(player);
            if (candidate == null || candidate.carriage.get() != car || player.level() != car.level()) continue;
            if (carriage(player) != car) continue;
            Standing s = STANDING.get().get(player);
            if (next == null) next = frame(car, 1);
            if (next.equals(s.frame)) continue;
            SupportFrameView previous = s.frame;
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

    static void placeFromLocal(Player player, Standing s, Vec3 world) {
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
                                          SupportFrameView frame) {
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
        SupportFrameView frame = frame(car, 1);
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
        CarriageClientPlacement.CLIENT_RESTORE.get().remove(player);
        CarriageClientPlacement.CLIENT_PLACEMENT_REPORT.get().remove(player);
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

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            STANDING.get().remove(player);
            CarriageClientPlacement.CLIENT_RESTORE.get().remove(player);
            CarriageClientPlacement.CLIENT_PLACEMENT_REPORT.get().remove(player);
            var pending = CarriageClientPlacement.PENDING_REMOTE.get().get(player.level());
            if (pending != null) pending.remove(player.getId());
        }
        if (event.getEntity() instanceof SnapshotCarriageEntity car) {
            STANDING.get().entrySet().removeIf(e -> e.getValue().carriage.get() == car);
            if (car.level().isClientSide) CarriageClientPlacement.CLIENT_RESTORE.get().entrySet().removeIf(entry -> {
                CarriageClientPlacement.ClientRestore restore = entry.getValue();
                if (restore.id() != car.getId() || !restore.uuid().equals(car.getUUID())) return false;
                // The target disappeared before placement. Release the same handoff
                // with the client's current world feet instead of waiting forever.
                Player player = entry.getKey();
                if (!player.isRemoved() && player.isLocalPlayer())
                    CarriageClientPlacement.reportClientPlacement(player, restore.id(), restore.uuid(), restore.token());
                return true;
            });
        }
    }
}
