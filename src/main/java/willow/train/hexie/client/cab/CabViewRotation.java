package willow.train.hexie.client.cab;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Mouse look in the cab frame. Minecraft's camera looks along local -Z. */
public final class CabViewRotation {
    private double yaw, pitch;
    public void start(Vector3f localLook) {
        localLook = new Vector3f(localLook).normalize();
        yaw = Math.toDegrees(Math.atan2(-localLook.x, localLook.z));
        pitch = Math.toDegrees(Math.asin(Math.clamp(-localLook.y, -1, 1)));
    }
    public void turn(double horizontal, double vertical) {
        yaw = Math.IEEEremainder(yaw + horizontal * .15, 360);
        pitch = Math.clamp(pitch + vertical * .15, -90, 90);
    }
    public Quaternionf world(Quaternionfc cabRotation) {
        return new Quaternionf(cabRotation).mul(new Quaternionf().rotationYXZ(
                (float) Math.toRadians(180 - yaw), (float) Math.toRadians(-pitch), 0));
    }
    /** The inverse of Camera.setRotation(yaw, pitch, roll). */
    public static Vector3f cameraAngles(Quaternionfc rotation) {
        var angles = rotation.getEulerAnglesYXZ(new Vector3f());
        return new Vector3f(180 - (float) Math.toDegrees(angles.y),
                -(float) Math.toDegrees(angles.x), -(float) Math.toDegrees(angles.z));
    }
}
