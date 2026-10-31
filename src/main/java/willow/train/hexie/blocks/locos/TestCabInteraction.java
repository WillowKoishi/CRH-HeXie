package willow.train.hexie.blocks.locos;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Surface hit test for captured driving-seat input only. */
public final class TestCabInteraction {
    private TestCabInteraction() {}
    public static int action(BlockState state, Vec3 originWorld, Vec3 directionWorld) {
        float angle = (float) Math.toRadians(state.getValue(CRH2DrivingConsoleBlock.FACING).toYRot() - 180);
        var origin = originWorld.add(-.5, 0, -.5).yRot(angle).add(.5, 0, .5);
        var direction = directionWorld.yRot(angle);
        if (Math.abs(direction.z) < 1e-7) return -1;
        double distance = (.915 - origin.z) / direction.z;
        if (distance < 0 || distance > 4) return -1;
        var hit = origin.add(direction.scale(distance));
        int action = -1;
        if (hit.y >= 1.20 && hit.y <= 1.62) {
            if (hit.x >= .06 && hit.x <= .29) action = hit.y >= 1.41 ? 0 : 1;
            if (hit.x >= .71 && hit.x <= .94) action = hit.y >= 1.41 ? 2 : 3;
        }
        var buttonHit = origin.add(direction.scale((.90 - origin.z) / direction.z));
        if (buttonHit.y >= 1.02 && buttonHit.y <= 1.16) {
            if (buttonHit.x >= .06 && buttonHit.x <= .32) action = 4;
            if (buttonHit.x >= .37 && buttonHit.x <= .63) action = 5;
            if (buttonHit.x >= .68 && buttonHit.x <= .94) action = 6;
        }
        // The DMI sits behind the handle panel; intersect its own button plane.
        double dmiDistance = (.856 - origin.z) / direction.z;
        var dmiHit = origin.add(direction.scale(dmiDistance));
        if (dmiDistance >= 0 && dmiDistance <= 4 && dmiHit.x >= .35 && dmiHit.x <= .39
                && dmiHit.y >= 1.61 && dmiHit.y <= 1.65) action = 5;
        return action;
    }
}
