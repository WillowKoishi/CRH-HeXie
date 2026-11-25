package willow.train.hexie.world.blocks.emu;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.world.collision.EmuCollision;

/** Authored pixels mapped to the north-facing door's local block coordinates. */
public final class CRH1DoorGeometry {
    public static final int ANIMATION_TICKS = 5 * 20;
    public static final float UNPLUG_PHASE = .25f;
    public static final double OUTWARD_TRAVEL = 1 / 16d;
    public static final double SOUTH_TRAVEL = 20 / 16d;

    public static final AABB RENDER_BOUNDS = new AABB(-15 / 16d, -15.7 / 16, 12.4 / 16,
            47 / 16d, 44 / 16d, 33 / 16d);

    private CRH1DoorGeometry() {}

    /** First move one pixel outward (authored +X), then twenty pixels toward authored +Z (south). */
    public static Vec3 offset(float progress, boolean mirrored) {
        float t = Mth.clamp(progress, 0, 1);
        if (t == 0) return Vec3.ZERO;
        double outward = smooth(t / UNPLUG_PHASE) * OUTWARD_TRAVEL;
        double slide = smooth((t - UNPLUG_PHASE) / (1 - UNPLUG_PHASE)) * SOUTH_TRAVEL;
        return new Vec3(mirrored ? -slide : slide, 0, -outward);
    }

    private static double smooth(float value) {
        double t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static AABB mirror(AABB box, boolean mirrored) {
        return EmuCollision.ForCRH1DoorGeometry.mirror(box, mirrored);
    }
}
