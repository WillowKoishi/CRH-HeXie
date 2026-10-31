package willow.train.hexie.client.events;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import willow.train.hexie.blocks.locos.DrivingConsoleContext;
import willow.train.hexie.client.cab.CabViewRotation;

/** Relative eye position and mouse look for captured seats; no dependency on a particular vehicle backend. */
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class DrivingConsoleCamera {
    private static DrivingConsoleContext active;
    private static Entity rider;
    private static Vec3 localEye;
    private static final CabViewRotation look = new CabViewRotation();
    private DrivingConsoleCamera() {}

    private static boolean update() {
        var player = Minecraft.getInstance().player;
        var context = DrivingConsoleClient.context();
        if (context == null || player == null || !player.isAlive()
                || player.getVehicle() != context.seatAccess().vehicle()) {
            active = null; rider = null; localEye = null;
            return false;
        }
        if (!context.equals(active) || rider != player) {
            active = context; rider = player;
            var frame = frame(1);
            localEye = active.seatAccess().toLocal(player.getEyePosition(1), 1);
            var direction = new Vector3f((float) player.getLookAngle().x, (float) player.getLookAngle().y,
                    (float) player.getLookAngle().z);
            look.start(frame.rotation.transformInverse(direction));
        }
        return true;
    }

    public static boolean turn(Entity entity, double horizontal, double vertical) {
        if (entity != Minecraft.getInstance().player || !DrivingConsoleClient.capturesInput() || !update()) return false;
        look.turn(horizontal, vertical);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (event.getCamera().getEntity() != Minecraft.getInstance().player || !update()) return;
        var angles = CabViewRotation.cameraAngles(look.world(frame((float) event.getPartialTick()).rotation));
        event.setYaw(angles.x); event.setPitch(angles.y); event.setRoll(angles.z);
        // Other gameplay code and dismount retain the current world-facing direction, not stale cab angles.
        var player = Minecraft.getInstance().player;
        player.setYRot(angles.x); player.yRotO = angles.x;
        player.setXRot(angles.y); player.xRotO = angles.y;
    }

    public static Vec3 position(Entity entity, boolean detached, float partial, Vec3 original) {
        if (entity != Minecraft.getInstance().player || !update()) return original;
        var frame = frame(partial);
        Vec3 offset = localEye.subtract(frame.localAnchor);
        var rotated = frame.rotation.transform(new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
        Vec3 eye = frame.worldAnchor.add(rotated.x, rotated.y, rotated.z);
        return detached ? original.add(eye.subtract(entity.getEyePosition(partial))) : eye;
    }

    private static Frame frame(float partial) {
        var seat = active.seatAccess();
        Vec3 anchor = seat.vehicle().position(), local = seat.toLocal(anchor, partial);
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
