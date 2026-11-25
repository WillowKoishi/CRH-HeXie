package willow.train.hexie.core.util;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Turn north-facing block geometry about the horizontal centre of the block. */
public final class BlockRotation {
    private BlockRotation() {}

    public static Vec3 rotate(Vec3 point, Direction facing) {
        double x = point.x - .5;
        double z = point.z - .5;
        return switch (facing) {
            case EAST -> new Vec3(.5 - z, point.y, .5 + x);
            case SOUTH -> new Vec3(.5 - x, point.y, .5 - z);
            case WEST -> new Vec3(.5 + z, point.y, .5 - x);
            default -> point;
        };
    }

    public static AABB rotate(AABB box, Direction facing) {
        return new AABB(rotate(new Vec3(box.minX, box.minY, box.minZ), facing),
                rotate(new Vec3(box.maxX, box.maxY, box.maxZ), facing));
    }
}
