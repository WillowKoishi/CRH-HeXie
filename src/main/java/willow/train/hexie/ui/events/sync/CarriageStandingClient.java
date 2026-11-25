package willow.train.hexie.ui.events.sync;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;

public final class CarriageStandingClient {
    private static Runnable pendingMovement;
    private static long movementEpoch;
    private record Dismount(Player player, SnapshotCarriageView carriage, Vec3 localFeet) {}
    private static Dismount pendingDismount;

    public static void afterCollisionMovement(Runnable movement) { pendingMovement = movement; }

    public static Entity mountedOrStanding(Player player) {
        var carriage = HexieBridges.createPlayer().mountedOrStanding(player);
        return carriage == null ? null : carriage.asEntity();
    }

    public static long movementEpoch() { return movementEpoch; }

    /** Capture Create's explicit seat removal before it replaces the old seat mapping. */
    public static void dismount(Player player, SnapshotCarriageView car, Vec3 localFeet) {
        if (Minecraft.getInstance().player != player) return;
        player.getPersistentData().remove("ContraptionDismountLocation");
        pendingDismount = new Dismount(player, car, localFeet);
    }

    private static void finishDismount() {
        Dismount exit = pendingDismount;
        if (exit == null) return;
        var player = Minecraft.getInstance().player;
        var car = exit.carriage();
        if (player != exit.player() || !player.isAlive() || player.isRemoved() || player.level() != car.asEntity().level()
                || player.isPassenger() && player.getVehicle() != car) {
            pendingDismount = null;
            return;
        }
        // Vanilla's passenger packet temporarily ejects and remounts its other
        // passengers. Only complete this explicit exit after that packet has finished.
        if (player.isPassenger()) return;
        pendingDismount = null;
        if (!car.asEntity().isRemoved() && exit.localFeet() != null) {
            // This writes frame.world(localFeet) through setPos, resets interpolation,
            // and forces the first client position report to the server.
            HexieBridges.createPlayer().startClientFollowing(player, car, exit.localFeet(), 0);
        } else {
            // Missing/removed seats still finish the server's handoff with one exit report.
            HexieBridges.createPlayer().detach(player);
            HexieBridges.createPlayer().reportClientPlacement(player, car.asEntity().getId(), car.asEntity().getUUID(), 0);
        }
    }

    /** A world teleport supersedes the previous standing identity and any queued report. */
    public static void resetMovement(Player player) {
        if (Minecraft.getInstance().player != player) return;
        pendingMovement = null;
        pendingDismount = null;
        movementEpoch++;
    }

    @SubscribeEvent public static void beforeTick(ClientTickEvent.Pre event) {
        var level = Minecraft.getInstance().level;
        if (level != null) HexieBridges.createPlayer().resolveRemote(level);
        finishDismount();
        HexieBridges.createPlayer().beginClientTick();
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void afterTick(ClientTickEvent.Post event) {
        // Create resolves contraption collisions in its normal-priority Post handler.
        // Resolve attachment even when vanilla emitted no packet, then send final feet.
        Runnable movement = pendingMovement;
        pendingMovement = null;
        if (movement != null) movement.run();
    }
    @SubscribeEvent public static void afterPlayer(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) HexieBridges.createPlayer().placeRemote(event.getEntity());
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void beforeRender(RenderPlayerEvent.Pre event) {
        var offset = modelOffset(event);
        event.getPoseStack().translate(offset.x, offset.y, offset.z);
    }
    @SubscribeEvent public static void afterRender(RenderPlayerEvent.Post event) {
        var offset = modelOffset(event);
        event.getPoseStack().translate(-offset.x, -offset.y, -offset.z);
    }

    private static Vec3 modelOffset(RenderPlayerEvent event) {
        var player = event.getEntity();
        // LevelRenderer interpolates the model from xOld; Camera interpolates from xo.
        var vanilla = new Vec3(player.xOld, player.yOld, player.zOld).lerp(player.position(), event.getPartialTick());
        if (player.getVehicle() instanceof SnapshotCarriageView car) {
            Vec3 seat = car.asEntity().getPassengerPosition(player, event.getPartialTick());
            if (seat != null) {
                // Reuse seat-specific placement and positionRider's feet offset, at the
                // rendered body origin rather than a straight chord between tick positions.
                // Create applies no custom species offset to players.
                Vec3 feet = seat.add(0, -.125, 0)
                        .add(car.renderPosition(event.getPartialTick()).subtract(car.asEntity().position()));
                return feet.subtract(vanilla);
            }
        }
        return HexieBridges.createPlayer().renderOffset(player, event.getPartialTick(), vanilla);
    }
}
