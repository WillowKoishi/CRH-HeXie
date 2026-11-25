package willow.train.hexie.ui.events.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.render.CabRenderBridge.CabViewRotationView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DriverSeatBlockView;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.ui.events.input.DrivingConsoleClient;
import willow.train.hexie.ui.events.sync.CarriageStandingClient;

/** One carriage-local look direction for the local player, whether standing, seated or driving. */
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class CarriageCamera {
    private static final CabViewRotationView look = HexieBridges.Client.cabRender().cabViewRotation().create();
    private static Player rider;
    private static Entity vehicle;
    private static DrivingConsoleContext cab;
    private static Vec3 cabEye;
    private static Vector3f facing;
    private static Camera pendingCamera;
    private static Vec3 standingOffset;
    private static boolean panorama;
    private CarriageCamera() {}

    private static boolean isViewer(Player player) {
        var mc = Minecraft.getInstance();
        return player != null && player == mc.player && player == mc.getCameraEntity()
                && player.isAlive() && !player.isSpectator();
    }

    public static boolean followsSeat(Player player) {
        return isViewer(player) && player.isPassenger() && (player.getVehicle() instanceof SnapshotCarriageView
                || DrivingConsoleClient.cameraCab() != null);
    }

    private static Quaternionf track(float partial) {
        var player = Minecraft.getInstance().player;
        DrivingConsoleContext nextCab = null;
        Entity next = null;
        if (isViewer(player)) {
            nextCab = DrivingConsoleClient.cameraCab();
            if (nextCab != null) next = nextCab.seatAccess().vehicle();
            else if (ModList.get().isLoaded("create")) {
                next = CarriageStandingClient.mountedOrStanding(player);
            }
        }
        if (next == null) {
            rider = null;
            vehicle = null;
            cab = null;
            cabEye = null;
            facing = null;
            return null;
        }
        var rotation = next instanceof SnapshotCarriageView car
                ? car.bodyRotation(partial) : frame(nextCab, partial).rotation;
        if (player != rider || next != vehicle || player.getYRot() != facing.x || player.getXRot() != facing.y) {
            // Board, transfer between carriages, or receive an explicit world-facing correction.
            look.start(rotation, player.getYRot(), player.getXRot());
        }
        if (nextCab != null && (player != rider || !nextCab.equals(cab))) {
            var seat = nextCab.seatAccess();
            var state = seat.blockState(seat.seat());
            cabEye = state != null && state.getBlock() instanceof CRH2DriverSeatBlockView
                    ? Vec3.atLowerCornerOf(seat.seat()).add(HexieBridges.carriageBlock().crh2DriverSeatBlock().driverEyePosition(state))
                    : seat.toLocal(player.getEyePosition(partial), partial);
        }
        rider = player;
        vehicle = next;
        cab = nextCab;
        return rotation;
    }

    private static void applyLook(Quaternionf rotation) {
        var player = rider;
        facing = HexieBridges.Client.cabRender().cabViewRotation().cameraAngles(look.world(rotation));
        facing.x = player.getYRot() + Mth.wrapDegrees(facing.x - player.getYRot());
        player.setYRot(facing.x);
        player.yRotO = facing.x;
        player.setXRot(facing.y);
        player.xRotO = facing.y;
        if (player.isPassenger()) {
            // A mounted LocalPlayer uses interpolated head yaw for its interaction ray.
            player.yHeadRot = facing.x;
            player.yHeadRotO = facing.x;
        }
    }

    private static void update(float partial) {
        var rotation = track(partial);
        if (rotation != null) applyLook(rotation);
    }

    /** Match movement input to the body pose at this player's tick, regardless of entity order. */
    public static void beforePlayerTick() {
        update(1);
    }

    @SubscribeEvent
    public static void beforeFrame(RenderFrameEvent.Pre event) {
        // Vanilla picks before Camera.setup. Project the same look for picking and rendering.
        var mc = Minecraft.getInstance();
        float partial = mc.level != null && mc.player != null && mc.level.tickRateManager().isEntityFrozen(mc.player)
                ? 1 : event.getPartialTick().getGameTimeDeltaPartialTick(true);
        update(partial);
    }

    public static boolean turn(Entity entity, double horizontal, double vertical) {
        var mc = Minecraft.getInstance();
        if (entity != mc.player || !isViewer(mc.player)) return false;
        // MouseHandler calls turn even in a frame with no mouse movement.
        if (horizontal == 0 && vertical == 0) return rider == entity;
        var rotation = track(mc.getTimer().getGameTimeDeltaPartialTick(true));
        if (rotation == null) return false;
        if (cab != null && !DrivingConsoleClient.capturesInput()) {
            applyLook(rotation);
            return false;
        }
        if (!DrivingConsoleClient.draggingHandle()) look.turn(horizontal, vertical);
        applyLook(rotation);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        Camera camera = event.getCamera();
        Entity entity = camera.getEntity();
        float partial = (float) event.getPartialTick();
        if (camera == event.getRenderer().getMainCamera()) {
            standingOffset = entity instanceof Player player && ModList.get().isLoaded("create")
                    ? HexieBridges.createPlayer().renderOffset(player, partial) : Vec3.ZERO;
            pendingCamera = !standingOffset.equals(Vec3.ZERO) || (entity == rider && cab != null) ? camera : null;
            // Panorama faces normally skip the FOV callback. Request it for an affected face,
            // then restore the fixed 90 degree projection before the world is drawn.
            panorama = pendingCamera != null && event.getRenderer().isPanoramicMode();
            if (panorama) event.getRenderer().setPanoramicMode(false);
        }
        if (rider == null || entity != rider) return;
        event.setYaw(facing.x);
        event.setPitch(facing.y);
        event.setRoll(facing.z);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void afterCameraSetup(ViewportEvent.ComputeFov event) {
        Camera camera = event.getCamera();
        if (!event.usedConfiguredFov() || camera != pendingCamera) return;
        pendingCamera = null;
        // Camera.setup writes the eye and applies third-person zoom AFTER ComputeCameraAngles.
        // The world projection is the first callback with that final position, before frustum setup.
        if (panorama) event.getRenderer().setPanoramicMode(true);
        boolean wasSubmerged = hasFluidFov(camera);
        camera.setPosition(camera.getPosition().add(standingOffset));
        camera.setPosition(position(camera.getEntity(), camera.isDetached(), camera.getPartialTickTime(), camera.getPosition()));
        if (panorama) {
            event.setFOV(90);
        } else if (wasSubmerged != hasFluidFov(camera)) {
            // Vanilla computed its fluid multiplier using the uncorrected eye immediately before this event.
            double multiplier = Mth.lerp(Minecraft.getInstance().options.fovEffectScale().get(), 1.0, 0.85714287F);
            event.setFOV(wasSubmerged ? event.getFOV() / multiplier : event.getFOV() * multiplier);
        }
    }

    private static boolean hasFluidFov(Camera camera) {
        FogType fluid = camera.getFluidInCamera();
        return fluid == FogType.WATER || fluid == FogType.LAVA;
    }

    public static Vec3 position(Entity entity, boolean detached, float partial, Vec3 original) {
        if (entity != rider || cab == null) return original;
        var frame = frame(cab, partial);
        Vec3 offset = cabEye.subtract(frame.localAnchor);
        var rotated = frame.rotation.transform(new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
        Vec3 eye = frame.worldAnchor.add(rotated.x, rotated.y, rotated.z);
        // The native third-person camera already includes Sable's render-pose interpolation.
        return detached ? original.add(eye.subtract(HexieBridges.sable().renderEye(entity, partial))) : eye;
    }

    private static Frame frame(DrivingConsoleContext context, float partial) {
        var seat = context.seatAccess();
        var vehicle = seat.vehicle();
        // Sable retains seat entities in distant storage plots. Use a world anchor so the
        // float rotation below only sees a small local offset and preserves the cushion offset.
        Vec3 anchor = HexieBridges.sable().render(vehicle.level(), vehicle.blockPosition(), partial)
                .toWorld(vehicle.position());
        Vec3 local = seat.toLocal(anchor, partial);
        Vec3 x = seat.toLocal(anchor.add(1, 0, 0), partial).subtract(local);
        Vec3 y = seat.toLocal(anchor.add(0, 1, 0), partial).subtract(local);
        Vec3 z = seat.toLocal(anchor.add(0, 0, 1), partial).subtract(local);
        // toLocal gives the inverse basis. Transposition restores the rigid cab-to-world rotation.
        var inverse = new Matrix3f((float) x.x, (float) x.y, (float) x.z,
                (float) y.x, (float) y.y, (float) y.z, (float) z.x, (float) z.y, (float) z.z);
        return new Frame(anchor, local, new Quaternionf().setFromNormalized(inverse.transpose()).normalize());
    }

    private record Frame(Vec3 worldAnchor, Vec3 localAnchor, Quaternionf rotation) {}
}
