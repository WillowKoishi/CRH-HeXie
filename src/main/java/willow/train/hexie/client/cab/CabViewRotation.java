package willow.train.hexie.client.cab;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Mouse look in the cab frame. Minecraft's camera looks along local -Z. */
public final class CabViewRotation {
    private double yaw, pitch;
    public void start(Quaternionfc frameRotation, float worldYaw, float worldPitch) {
        var local = new Quaternionf(frameRotation).conjugate().mul(viewRotation(worldYaw, worldPitch));
        var angles = cameraAngles(local);
        yaw = angles.x;
        pitch = angles.y;
    }
    public void turn(double horizontal, double vertical) {
        yaw = Math.IEEEremainder(yaw + horizontal * .15, 360);
        pitch = Math.clamp(pitch + vertical * .15, -90, 90);
    }
    public Quaternionf world(Quaternionfc cabRotation) {
        return new Quaternionf(cabRotation).mul(viewRotation(yaw, pitch));
    }
    private static Quaternionf viewRotation(double yaw, double pitch) {
        return new Quaternionf().rotationYXZ((float) Math.toRadians(180 - yaw), (float) Math.toRadians(-pitch), 0);
    }
    /** The inverse of Camera.setRotation(yaw, pitch, roll). */
    public static Vector3f cameraAngles(Quaternionfc rotation) {
        double x = rotation.x(), y = rotation.y(), z = rotation.z(), w = rotation.w();
        double backX = 2 * (x * z + w * y);
        double backZ = w * w - x * x - y * y + z * z;
        double horizontal = Math.hypot(backX, backZ);
        double pitch = Math.atan2(2 * (w * x - y * z), horizontal);
        double yaw, roll;
        if (horizontal < 1e-6) {
            // Looking straight up or down leaves one combined yaw/roll angle. The camera's
            // right axis still gives that heading even when its forward axis is vertical.
            yaw = Math.atan2(2 * (w * y - x * z), w * w + x * x - y * y - z * z);
            roll = 0;
        } else {
            yaw = Math.atan2(backX, backZ);
            roll = Math.atan2(2 * (x * y + w * z), w * w - x * x + y * y - z * z);
        }
        return new Vector3f(180 - (float) Math.toDegrees(yaw),
                -(float) Math.toDegrees(pitch), -(float) Math.toDegrees(roll));
    }
}
