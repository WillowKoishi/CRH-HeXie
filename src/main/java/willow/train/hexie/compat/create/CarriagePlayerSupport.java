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
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import willow.train.hexie.Main;
import willow.train.hexie.vehicle.player.CarriedPlayerConnection;
import willow.train.hexie.vehicle.player.SupportFrame;

/** One support owner per player; never feeds back into the vehicle's motion solver. */
@EventBusSubscriber(modid = Main.MOD_ID)
public final class CarriagePlayerSupport {
    // Entity.equals/hashCode use the network id. In an integrated server the client
    // and server player share that id, but must never share a support frame.
    // MapMaker weak keys use object identity and still allow unloaded players to GC.
    private static final Map<Player, Standing> STANDING = new MapMaker().weakKeys().makeMap();
    private static final class Standing {
        final WeakReference<SnapshotCarriageEntity> carriage;
        SupportFrame frame;
        Vec3 local, previousLocal, remoteTarget, velocity = Vec3.ZERO;
        int remoteSteps;
        long lastAdvanceTick = Long.MIN_VALUE;
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
        Vec3 anchor = carriage.getPrevPositionVec().lerp(carriage.position(), partial);
        origin = origin.add(anchor.subtract(carriage.position()));
        return new SupportFrame(origin, carriage.applyRotation(new Vec3(1, 0, 0), partial),
                carriage.applyRotation(new Vec3(0, 1, 0), partial),
                carriage.applyRotation(new Vec3(0, 0, 1), partial));
    }

    public static synchronized SnapshotCarriageEntity carriage(Player player) {
        Standing s = STANDING.get(player);
        if (s == null) return null;
        SnapshotCarriageEntity car = s.carriage.get();
        if (car == null || car.isRemoved() || car.level() != player.level() || !eligible(player)) {
            STANDING.remove(player);
            return null;
        }
        return car;
    }

    private static boolean eligible(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.getAbilities().flying && !player.isFallFlying();
    }

    public static synchronized boolean supportedBy(Entity entity, SnapshotCarriageEntity car) {
        return entity instanceof Player player && carriage(player) == car;
    }

    /** Called after the body advances, before Create builds its collision candidate list. */
    public static synchronized void advance(SnapshotCarriageEntity car) {
        // Copy only players supported by this body; entity removal is allowed during callbacks.
        for (Player player : STANDING.keySet().toArray(Player[]::new)) {
            if (carriage(player) != car) continue;
            if (player instanceof ServerPlayer server && server.connection instanceof CarriedPlayerConnection connection
                    && !connection.hexie$canCarry()) {
                detach(player, false);
                continue;
            }
            Standing s = STANDING.get(player);
            SupportFrame next = frame(car, 1);
            long tick = player.level().getGameTime();
            if (next.equals(s.frame)) {
                if (s.lastAdvanceTick != tick) s.velocity = Vec3.ZERO;
                s.lastAdvanceTick = tick;
                continue;
            }
            s.lastAdvanceTick = tick;
            Vec3 local = player.level().isClientSide && !player.isLocalPlayer() ? s.local : s.frame.local(player.position());
            Vec3 carried = next.world(local);
            Vec3 displacement = carried.subtract(player.position());
            s.local = local;
            s.frame = next;
            s.velocity = displacement;
            player.setPos(carried);
            player.fallDistance = 0;
            if (player instanceof ServerPlayer server) {
                if (server.connection instanceof CarriedPlayerConnection connection) connection.hexie$carry(displacement);
                if (server.connection instanceof CarriedPlayerConnection connection) connection.hexie$grounded();
            }
        }
    }

    public static synchronized void contact(SnapshotCarriageEntity car, Entity entity) {
        if (!(entity instanceof Player player) || !eligible(player)
                || player.level().isClientSide && !player.isLocalPlayer()
                || player.getDeltaMovement().y > .05 || !hasFloor(car, player.position(), player.getBbWidth())) return;
        SnapshotCarriageEntity previous = carriage(player);
        if (previous != null && previous != car && hasFloor(previous, player.position(), player.getBbWidth())) return;
        if (previous != car) STANDING.put(player, new Standing(car, frame(car, 1).local(player.position())));
        player.setOnGround(true);
        player.verticalCollisionBelow = true;
        player.fallDistance = 0;
    }

    public static synchronized void afterCollision(SnapshotCarriageEntity car) {
        for (Player player : STANDING.keySet().toArray(Player[]::new)) {
            if (carriage(player) != car || !player.level().isClientSide || !player.isLocalPlayer()) continue;
            if (!hasFloor(car, player.position(), player.getBbWidth())) detach(player, true);
            else {
                Standing s = STANDING.get(player);
                s.local = s.frame.local(player.position());
                player.setOnGround(true);
                player.verticalCollisionBelow = true;
                player.fallDistance = 0;
            }
        }
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

    public static synchronized void detach(Player player, boolean inherit) {
        Standing s = STANDING.remove(player);
        if (s != null && inherit) player.setDeltaMovement(player.getDeltaMovement().add(s.velocity));
    }

    public static synchronized Vec3 localPosition(Player player) {
        SnapshotCarriageEntity car = carriage(player);
        return car == null ? player.position() : frame(car, 1).local(player.position());
    }

    public static synchronized void accept(Player player, SnapshotCarriageEntity car, Vec3 local) {
        Standing old = STANDING.get(player);
        if (old == null || old.carriage.get() != car) STANDING.put(player, new Standing(car, local));
        else { old.previousLocal = old.local; old.local = local; old.frame = frame(car, 1); }
        player.setOnGround(true);
        player.verticalCollisionBelow = true;
        player.fallDistance = 0;
    }

    public static synchronized Vec3 renderOffset(Player player, float partial) {
        SnapshotCarriageEntity car = carriage(player);
        if (car == null) return Vec3.ZERO;
        Standing s = STANDING.get(player);
        Vec3 local = s.previousLocal.lerp(frame(car, 1).local(player.position()), partial);
        Vec3 expected = frame(car, partial).world(local);
        Vec3 vanilla = new Vec3(player.xo, player.yo, player.zo).lerp(player.position(), partial);
        return expected.subtract(vanilla);
    }

    public static synchronized void acceptRemote(Player player, SnapshotCarriageEntity car, Vec3 local) {
        if (carriage(player) != car) {
            accept(player, car, local);
            player.setPos(frame(car, 1).world(local));
        } else {
            Standing s = STANDING.get(player);
            s.remoteTarget = local;
            s.remoteSteps = 3;
        }
    }

    public static synchronized void beginClientTick() {
        for (Player player : STANDING.keySet().toArray(Player[]::new)) {
            if (!player.level().isClientSide || carriage(player) == null) continue;
            beginClientTick(player, player.isLocalPlayer());
        }
    }

    static synchronized void beginClientTick(Player player, boolean localPlayer) {
        Standing s = STANDING.get(player);
        if (s == null) return;
        // Walking/collision callbacks may run after the last cached support coordinate.
        // The next interpolation must start exactly where the preceding frame ended.
        if (localPlayer) s.local = s.frame.local(player.position());
        s.previousLocal = s.local;
        if (!localPlayer && s.remoteSteps > 0)
            s.local = s.local.lerp(s.remoteTarget, 1.0 / s.remoteSteps--);
    }

    public static synchronized void placeRemote(Player player) {
        var car = carriage(player);
        if (car != null && !player.isLocalPlayer()) player.setPos(frame(car, 1).world(STANDING.get(player).local));
    }

    @SubscribeEvent public static synchronized void jump(LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player) detach(player, true);
    }
    @SubscribeEvent public static synchronized void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Player player) STANDING.remove(player);
        if (event.getEntity() instanceof SnapshotCarriageEntity car)
            STANDING.entrySet().removeIf(e -> e.getValue().carriage.get() == car);
    }
}
