package willow.train.hexie.client.render.bogey;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Align a rod to its two attachment points without changing its transverse dimensions. */
public final class BogeyLinkTransform {
    private BogeyLinkTransform() {}

    /** Follow the body's attachment point while preserving the part's orientation and dimensions. */
    public static Matrix4f translationAt(Matrix4fc body, Vector3fc bodyAnchor) {
        return new Matrix4f().translation(body.transformPosition(bodyAnchor, new Vector3f()).sub(bodyAnchor));
    }

    /** Add the change in anchor spacing to the piston reach, retaining insertion into a rigid sleeve. */
    public static Matrix4f telescopicPiston(Vector3fc frameAnchor, Vector3fc bodyAnchor,
                                            Vector3fc movingBodyAnchor, float restReach) {
        Vector3f axis = new Vector3f(bodyAnchor).sub(frameAnchor);
        float restDistance = axis.length();
        if (restDistance < 1e-6f || restReach < 1e-6f)
            return between(frameAnchor, bodyAnchor, movingBodyAnchor, false);
        float distance = movingBodyAnchor.distance(frameAnchor);
        float reach = Math.max(restReach + distance - restDistance, 1e-5f);
        // Keep alignment based on the full anchor separation, even when the piston is nearly collapsed.
        return between(frameAnchor, bodyAnchor, movingBodyAnchor, false).translate(frameAnchor)
                .mul(axialScale(axis.div(restDistance), reach / restReach))
                .translate(-frameAnchor.x(), -frameAnchor.y(), -frameAnchor.z());
    }

    public static Matrix4f between(Vector3fc frameAnchor, Vector3fc bodyAnchor,
                                   Vector3fc movingBodyAnchor, boolean stretch) {
        return between(frameAnchor, bodyAnchor, frameAnchor, movingBodyAnchor, stretch);
    }

    /** Also supports a rigid sleeve whose body-side pivot moves while its frame-side pivot stays fixed. */
    public static Matrix4f between(Vector3fc frameAnchor, Vector3fc bodyAnchor,
                                   Vector3fc movingFrameAnchor, Vector3fc movingBodyAnchor, boolean stretch) {
        Vector3f axis = new Vector3f(bodyAnchor).sub(frameAnchor);
        Vector3f direction = new Vector3f(movingBodyAnchor).sub(movingFrameAnchor);
        float originalLength = axis.length(), length = direction.length();
        // Coincident attachment points have no direction. Keep a finite, collapsed rod.
        if (originalLength < 1e-6f) return new Matrix4f().translation(new Vector3f(movingFrameAnchor).sub(frameAnchor));
        axis.div(originalLength);
        Quaternionf rotation = length < 1e-6f ? new Quaternionf()
                : new Quaternionf().rotationTo(axis, direction.div(length));
        Matrix4f result = new Matrix4f().translation(movingFrameAnchor).rotate(rotation);
        if (stretch) result.mul(axialScale(axis, Math.max(length / originalLength, 1e-5f)));
        return result.translate(-frameAnchor.x(), -frameAnchor.y(), -frameAnchor.z());
    }

    private static Matrix4f axialScale(Vector3f axis, float scale) {
        float k = scale - 1;
        // I + (scale - 1) nn^T scales only along the original rod axis.
        return new Matrix4f()
                .m00(1 + k * axis.x * axis.x).m01(k * axis.x * axis.y).m02(k * axis.x * axis.z)
                .m10(k * axis.y * axis.x).m11(1 + k * axis.y * axis.y).m12(k * axis.y * axis.z)
                .m20(k * axis.z * axis.x).m21(k * axis.z * axis.y).m22(1 + k * axis.z * axis.z);
    }
}
