package willow.train.hexie.interaction.surface;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.BlockControlHost;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.interaction.spatial.SpatialPicker;
import willow.train.hexie.interaction.spatial.SurfaceIndex.PartBounds;

/** Shared client detection over the surface geometry and full-motion bounds declared by each block. */
public final class SurfaceControlPicking {
    private static final double EPSILON = 1e-9;
    private static final Map<ControlSurface, PartBounds> INDEXES = new IdentityHashMap<>();

    private SurfaceControlPicking() {}

    /** Input and output points use the target's storage/carrier-local coordinates. */
    public static BlockControlHit pick(BlockControlHost host, BlockControlContext target, Vec3 from, Vec3 to) {
        var blockState = target.state();
        var surface = host.surfaceGeometry(blockState);
        Vec3 offset = Vec3.atLowerCornerOf(target.position());
        Vec3 origin = from.subtract(offset), direction = to.subtract(from);
        double lengthSquared = direction.lengthSqr(), reach = Math.sqrt(lengthSquared);
        if (surface.parts().isEmpty() || !Double.isFinite(reach) || reach <= 0
                || !Double.isFinite(origin.lengthSqr()) || !Double.isFinite(lengthSquared) || lengthSquared == 0)
            return null;
        Vec3 unitRay = direction.scale(1 / reach);
        float inverseYaw = (float) Math.toRadians(-ControlSurface.yaw(host.surfaceFacing(blockState)));
        Vec3 localFrom = origin.add(-.5, 0, -.5).yRot(inverseYaw).add(.5, 0, .5);
        Vec3 localRay = unitRay.yRot(inverseYaw);
        var state = host.surfaceState(target);
        var animation = host.animation(target);
        var index = INDEXES.computeIfAbsent(surface, SurfaceControlIndex::build);
        var hit = SpatialPicker.pickParts(index, SurfaceRay.vec(localFrom), SurfaceRay.vec(localRay), reach, (i, limit) -> {
            var part = surface.parts().get(i);
            Vec3 local = localFrom, axis = localRay;
            if (part.pose() != null) {
                var inverse = part.pose().at(state, animation).invert(new Matrix4f());
                local = new Vec3(inverse.transformPosition(localFrom.toVector3f()));
                axis = new Vec3(inverse.transformDirection(localRay.toVector3f()));
            }
            return part.shape().hit(local, axis, limit);
        });
        if (hit.isEmpty()) return null;
        double nearest = hit.get().distance();
        if (!(surface.body().hit(localFrom, localRay, nearest) >= nearest - EPSILON)) return null;
        var selected = surface.parts().get(hit.get().part()).control();
        Vec3 point = origin.add(unitRay.scale(nearest)).add(offset);
        var face = Direction.getNearest(-direction.x, -direction.y, -direction.z);
        return new BlockControlHit(selected, new BlockHitResult(point, face, target.position(), false));
    }
}
