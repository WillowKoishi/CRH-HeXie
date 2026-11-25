package willow.train.hexie.render.bogey;

import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import willow.train.hexie.bridge.railway.TrackRegistryBridge;
import willow.train.hexie.bridge.render.BogeyRenderBridge.SimurailBogeySuspensionPoseAccessView;

/** Both roots are expressed in the containing sublevel, so its world pose cancels out. */
public final class SimurailBogeySuspensionPose {
    private static final float MODEL_Y = (float) (-1.5 - 1 / 128.0 + TrackRegistryBridge.RAIL_HEIGHT_INCREASE);
    private static final float HALF_PI = (float) (Math.PI / 2);

    private SimurailBogeySuspensionPose() {}

    /** Avoids linking the optional Sable/Simurail implementation from the base client. */
    public interface Access extends SimurailBogeySuspensionPoseAccessView {}

    public static Matrix4f relative(Object bogey, float partialTick) {
        return ((SimurailBogeySuspensionPoseAccessView) bogey).hexie$suspensionPose(partialTick);
    }

    public static Matrix4f relative(Vector3fc offset, Quaternionfc rotation, Quaternionfc restRotation, float axleOffset) {
        Matrix4f moving = new Matrix4f().translation(offset).rotate(rotation)
                .translate(0, axleOffset, 0)
                .rotateY(HALF_PI).translate(0, MODEL_Y, 0);
        // The upper mount belongs to the control block, not the movable pivot or axle offset.
        Matrix4f fixed = new Matrix4f().rotation(restRotation)
                .rotateY(HALF_PI).translate(0, MODEL_Y, 0);
        return moving.invert().mul(fixed);
    }
}
