package willow.train.hexie.client.render.bogey;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Align a rod to its two attachment points without changing its transverse dimensions. */
public final class BogeyLinkTransform {
    private BogeyLinkTransform() {}

    public static Matrix4f between(Vector3fc frameAnchor, Vector3fc bodyAnchor,
                                   Vector3fc movingBodyAnchor, boolean stretch) {
        Vector3f axis = new Vector3f(bodyAnchor).sub(frameAnchor);
        Vector3f direction = new Vector3f(movingBodyAnchor).sub(frameAnchor);
        float originalLength = axis.length(), length = direction.length();
        // Coincident attachment points have no direction. Keep a finite, collapsed rod.
        if (originalLength < 1e-6f) return new Matrix4f();
        axis.div(originalLength);
        Quaternionf rotation = length < 1e-6f ? new Quaternionf()
                : new Quaternionf().rotationTo(axis, direction.div(length));
        Matrix4f result = new Matrix4f().translation(frameAnchor).rotate(rotation);
        if (stretch) {
            float k = Math.max(length / originalLength, 1e-5f) - 1;
            // I + (scale - 1) nn^T scales only along the original rod axis.
            Matrix4f axialScale = new Matrix4f()
                    .m00(1 + k * axis.x * axis.x).m01(k * axis.x * axis.y).m02(k * axis.x * axis.z)
                    .m10(k * axis.y * axis.x).m11(1 + k * axis.y * axis.y).m12(k * axis.y * axis.z)
                    .m20(k * axis.z * axis.x).m21(k * axis.z * axis.y).m22(1 + k * axis.z * axis.z);
            result.mul(axialScale);
        }
        return result.translate(-frameAnchor.x(), -frameAnchor.y(), -frameAnchor.z());
    }
}
