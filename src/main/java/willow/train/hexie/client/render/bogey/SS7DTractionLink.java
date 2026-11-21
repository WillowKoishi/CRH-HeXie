package willow.train.hexie.client.render.bogey;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** A rigid rod pivots at the frame and slides through a body-mounted guide; it never stretches. */
public final class SS7DTractionLink {
    private SS7DTractionLink() {}

    public static Vector3f frameAnchor(boolean left) {
        return new Vector3f(left ? -19.2f / 16 : 19.2f / 16, 10.1f / 16, left ? 31.05f / 16 : -31.05f / 16);
    }

    public static Vector3f bodyGuide(boolean left) {
        // Intersect the authored rod centreline (including the left rod's 3-degree yaw)
        // with the body bracket's Z midplane. Keep the exact authored resting pose.
        Vector3f axis = new Vector3f(0, 0, left ? 1 : -1);
        if (left) axis.rotateY((float) Math.toRadians(3));
        axis.rotateX((float) Math.toRadians(left ? -3.75 : 3.75));
        Vector3f pivot = frameAnchor(left);
        float distance = ((left ? 44.625f : -44.625f) / 16 - pivot.z) / axis.z;
        return pivot.fma(distance, axis);
    }

    public static Matrix4f transform(Matrix4fc bodyInFrame, boolean left) {
        Vector3f guide = bodyGuide(left);
        return BogeyLinkTransform.between(frameAnchor(left), guide,
                bodyInFrame.transformPosition(guide, new Vector3f()), false);
    }
}
