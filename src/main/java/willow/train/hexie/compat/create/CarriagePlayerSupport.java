package willow.train.hexie.compat.create;

import java.lang.ref.WeakReference;
import java.util.Map;
import com.google.common.collect.MapMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import willow.train.hexie.Main;
import willow.train.hexie.vehicle.player.CarriedPlayerConnection;
import willow.train.hexie.vehicle.player.SupportFrame;

/** One support owner per player; never feeds back into the vehicle's motion solver. */
@EventBusSubscriber(modid = Main.MOD_ID)
public final class CarriagePlayerSupport {
    // Tick/render callbacks run on their owning game thread; packet handlers enqueue
    // there too. Never share players or a lock between the integrated server and client:
    // collision queries can wait for server chunk work. Weak identity keys also keep
    // equal client/server network IDs distinct and allow unloaded players to be collected.
    private static final ThreadLocal<Map<Player, Standing>> STANDING =
            ThreadLocal.withInitial(() -> new MapMaker().weakKeys().makeMap());
    private record ClientRestore(int id, java.util.UUID uuid, Vec3 local) {}
    private static final ThreadLocal<Map<Player, ClientRestore>> CLIENT_RESTORE =
            ThreadLocal.withInitial(() -> new MapMaker().weakKeys().makeMap());

    public static void queueClientRestore(Player player, int id, java.util.UUID uuid, Vec3 local) {
        CLIENT_RESTORE.get().put(player, new ClientRestore(id, uuid, local));
    }

    private static final class Standing {
        final WeakReference<SnapshotCarriageEntity> carriage;
        SupportFrame frame;
        Vec3 local, previousLocal, remoteTarget;
        boolean placingFromLocal;
        int remoteSteps;
        Standing(SnapshotCarriageEntity carriage, Vec3 local) {
            this.carriage = new WeakReference<>(carriage);
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
                || (!(player.level().isClientSide && !player.isLocalPlayer()) && touchesWorld(player))) {
            STANDING.get().remove(player);
            return null;
        }
        return car;
    }

    private static boolean eligible(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.getAbilities().flying && !player.isFallFlying();
    }

    public static boolean supportedBy(Entity entity, SnapshotCarriageEntity car) {
        return entity instanceof Player player && carriage(player) == car;
    }

    /** Called after the body advances, before Create builds its collision candidate list. */
    public static void advance(SnapshotCarriageEntity car) {
        // Copy only players supported by this body; entity removal is allowed during callbacks.
        for (Player player : STANDING.get().keySet().toArray(Player[]::new)) {
            Standing candidate = STANDING.get().get(player);
            if (candidate == null || candidate.carriage.get() != car || player.level() != car.level()) continue;
            if (carriage(player) != car) continue;
            if (player instanceof ServerPlayer server && server.connection instanceof CarriedPlayerConnection connection
                    && !connection.hexie$canCarry()) {
                detach(player);
                continue;
            }
            Standing s = STANDING.get().get(player);
            SupportFrame next = frame(car, 1);
            if (next.equals(s.frame)) continue;
            Vec3 carried = next.world(s.local);
            Vec3 displacement = carried.subtract(player.position());
            s.frame = next;
            // Sweep against real blocks so a fast carriage cannot carry the player through a wall.
            Vec3 allowed = Entity.collideBoundingBox(player, displacement, player.getBoundingBox(),
                    player.level(), java.util.List.of());
            placeFromLocal(player, s, player.position().add(allowed));
            boolean worldHit = allowed.distanceToSqr(displacement) > 1e-12;
            if (worldHit) detach(player);
            if (player instanceof ServerPlayer server) {
                if (server.connection instanceof CarriedPlayerConnection connection) connection.hexie$carry(allowed);
                if (server.connection instanceof CarriedPlayerConnection connection) connection.hexie$grounded();
            }
        }
    }

    public static void contact(SnapshotCarriageEntity car, Entity entity) {
        if (!(entity instanceof Player player) || !eligible(player)
                || player.level().isClientSide && !player.isLocalPlayer()
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

    public static void afterCollision(SnapshotCarriageEntity car) {
        for (Player player : STANDING.get().keySet().toArray(Player[]::new)) {
            if (player.level() == car.level()) carriage(player);
        }
    }

    /** Only real world block shapes end attachment; other carriages do not. */
    public static boolean touchesWorld(Player player) {
        return player.level().getBlockCollisions(player, player.getBoundingBox().inflate(1e-5))
                .iterator().hasNext();
    }

    public static boolean touchesCarriage(SnapshotCarriageEntity car, Player player) {
        if (car.getContraption() == null) return false;
        var box = player.getBoundingBox().inflate(.05);
        var frame = frame(car, 1);
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

    public static Vec3 localPosition(Player player) {
        SnapshotCarriageEntity car = carriage(player);
        return car == null ? player.position() : STANDING.get().get(player).local;
    }

    public static void accept(Player player, SnapshotCarriageEntity car, Vec3 local) {
        Standing old = STANDING.get().get(player);
        if (old == null || old.carriage.get() != car) STANDING.get().put(player, new Standing(car, local));
        else { old.previousLocal = old.local; old.local = local; old.frame = frame(car, 1); }

    }

    public static Vec3 renderOffset(Player player, float partial) {
        SnapshotCarriageEntity car = carriage(player);
        if (car == null) return Vec3.ZERO;
        Standing s = STANDING.get().get(player);
        Vec3 local = s.previousLocal.lerp(s.local, partial);
        Vec3 expected = frame(car, partial).world(local);
        Vec3 vanilla = new Vec3(player.xo, player.yo, player.zo).lerp(player.position(), partial);
        return expected.subtract(vanilla);
    }

    public static void acceptRemote(Player player, SnapshotCarriageEntity car, Vec3 local) {
        if (carriage(player) != car) {
            accept(player, car, local);
            placeFromLocal(player, STANDING.get().get(player), frame(car, 1).world(local));
        } else {
            Standing s = STANDING.get().get(player);
            s.remoteTarget = local;
            s.remoteSteps = 3;
        }
    }

    public static void beginClientTick() {
        for (Player player : CLIENT_RESTORE.get().keySet().toArray(Player[]::new)) {
            var restore = CLIENT_RESTORE.get().get(player);
            if (player.isRemoved()) { CLIENT_RESTORE.get().remove(player); continue; }
            if (player.level().getEntity(restore.id()) instanceof SnapshotCarriageEntity car
                    && car.getUUID().equals(restore.uuid())) {
                accept(player, car, restore.local());
                placeFromLocal(player, STANDING.get().get(player), frame(car, 1).world(restore.local()));
                player.xo = player.getX(); player.yo = player.getY(); player.zo = player.getZ();
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
        if (!localPlayer && s.remoteSteps > 0)
            s.local = s.local.lerp(s.remoteTarget, 1.0 / s.remoteSteps--);
    }

    public static void placeRemote(Player player) {
        var car = carriage(player);
        if (car != null && !player.isLocalPlayer())
            placeFromLocal(player, STANDING.get().get(player), frame(car, 1).world(STANDING.get().get(player).local));
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            STANDING.get().remove(player);
            CLIENT_RESTORE.get().remove(player);
        }
        if (event.getEntity() instanceof SnapshotCarriageEntity car)
            STANDING.get().entrySet().removeIf(e -> e.getValue().carriage.get() == car);
    }
}
