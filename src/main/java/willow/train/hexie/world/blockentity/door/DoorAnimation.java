package willow.train.hexie.world.blockentity.door;

import net.minecraft.util.Mth;

/** Previous and current tick samples for a linearly moving door. */
final class DoorAnimation {
    private float previous;
    private float current;

    void set(float previous, float current) {
        this.previous = previous;
        this.current = current;
    }

    void reset(float value) {
        set(value, value);
    }

    void advance(float target, float speed) {
        previous = current;
        // The original chaser clamps and interpolates in double precision.
        current = Mth.equal((double) current, target) ? target
                : (float) (current + Mth.clamp((double) target - current, -speed, speed));
    }

    float value(float partialTick) {
        return (float) Mth.lerp((double) partialTick, previous, current);
    }
}
