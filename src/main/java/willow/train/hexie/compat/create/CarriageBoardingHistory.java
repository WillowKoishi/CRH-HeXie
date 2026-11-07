package willow.train.hexie.compat.create;

import java.util.ArrayDeque;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/** Retain recently visible bodies while a delayed client is stepping aboard. No player physics. */
final class CarriageBoardingHistory {
    private static final class Sample {
        final long stamp;
        final Vec3 position, velocity;
        final Quaternionf rotation;
        long lastTick;

        Sample(long tick, Vec3 position, Vec3 velocity, Quaternionfc rotation) {
            this.stamp = this.lastTick = tick;
            this.position = position;
            this.velocity = velocity;
            this.rotation = new Quaternionf(rotation);
        }

    }
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private static final int MAX_AGE_TICKS = 100;

    void clear() { samples.clear(); }

    boolean wasInTrackingRange(Vec3 observer, double range, long now) {
        double squaredRange = range * range;
        for (Sample sample : samples) {
            if (now - sample.lastTick > MAX_AGE_TICKS) continue;
            double dx = observer.x - sample.position.x, dz = observer.z - sample.position.z;
            if (dx * dx + dz * dz <= squaredRange) return true;
        }
        return false;
    }

    long record(long tick, Vec3 position, Vec3 velocity, Quaternionfc rotation) {
        while (!samples.isEmpty() && samples.getFirst().lastTick < tick - MAX_AGE_TICKS) samples.removeFirst();
        Sample last = samples.peekLast();
        if (last != null && last.position.equals(position)
                && last.velocity.equals(velocity) && last.rotation.equals(rotation)) {
            // A stopped carriage keeps the same network payload, however long it stands here.
            last.lastTick = tick;
            return last.stamp;
        }
        samples.addLast(new Sample(tick, position, velocity, rotation));
        return tick;
    }

}
