package willow.train.hexie.core.api.interaction;

import static willow.train.hexie.core.api.interaction.ControlSurface.*;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

// split from willow/train/hexie/api/interaction/ControlSurface.java
final class ControlSurfacePicking {
    public static float yaw(Direction facing) { return 180f - facing.toYRot(); }

    static Pick pick(ControlSurface surface, Direction facing, Vec3 origin, Vec3 direction, double reach, ControlState controls) {
        return pick(surface, facing, origin, direction, reach, controls, Animation.AT_TARGET);
    }

    /** Tests only this surface, with a finite reach and the same nearest-part/body-occlusion rule as hit. */
    static Pick pick(ControlSurface surface, Direction facing, Vec3 origin, Vec3 direction, double reach, ControlState controls, Animation animation) {
        double lengthSquared = direction.lengthSqr();
        if (surface.parts().isEmpty() || !Double.isFinite(reach) || reach <= 0
                || !Double.isFinite(origin.lengthSqr()) || !Double.isFinite(lengthSquared) || lengthSquared == 0)
            return null;
        Vec3 unitRay = direction.scale(1 / Math.sqrt(lengthSquared));
        float inverseYaw = (float) Math.toRadians(-yaw(facing));
        var from = origin.add(-.5, 0, -.5).yRot(inverseYaw).add(.5, 0, .5);
        var ray = unitRay.yRot(inverseYaw);
        double nearest = reach;
        BlockControl result = null;
        for (var part : surface.parts()) {
            Vec3 local = from, axis = ray;
            if (part.pose() != null) {
                var inverse = part.pose().at(controls, animation).invert(new Matrix4f());
                local = new Vec3(inverse.transformPosition(from.toVector3f()));
                axis = new Vec3(inverse.transformDirection(ray.toVector3f()));
            }
            double distance = part.shape().hit(local, axis, nearest);
            if (distance >= 0 && distance < nearest) {
                result = part.control();
                nearest = distance;
            }
        }
        return result != null && surface.body().hit(from, ray, nearest) >= nearest - EPSILON
                ? new Pick(result, nearest, origin.add(unitRay.scale(nearest))) : null;
    }

    static BlockControl hit(ControlSurface surface, Direction facing, Vec3 origin, Vec3 direction, ControlState controls) {
        return hit(surface, facing, origin, direction, controls, Animation.AT_TARGET);
    }

    /** Retains the existing four-block surface reach for callers that only need the selected control. */
    static BlockControl hit(ControlSurface surface, Direction facing, Vec3 origin, Vec3 direction, ControlState controls, Animation animation) {
        var pick = pick(surface, facing, origin, direction, 4, controls, animation);
        return pick == null ? null : pick.control();
    }
}
