package willow.train.hexie.compat.create;

import net.minecraft.world.phys.Vec3;

/** A single clock for a received pose; tangents are world displacement per game tick. */
public final class CarriagePoseInterpolation {
    private Vec3 from, to, fromVelocity = Vec3.ZERO, toVelocity = Vec3.ZERO;
    private int duration = 1, elapsed, previousElapsed;
    private Vec3 pendingPosition, pendingVelocity;
    private int pendingTicks;

    public boolean initialized() { return to != null; }

    public void accept(Vec3 position, Vec3 velocity, int ticks) {
        if (to == null || ticks <= 0) {
            from = to = position;
            fromVelocity = toVelocity = velocity;
            duration = 1;
            previousElapsed = elapsed = 1;
            return;
        }
        // Network callbacks may arrive between rendered frames. Do not replace the
        // current tick's interpolation endpoints until the next simulation tick.
        pendingPosition = position;
        pendingVelocity = velocity;
        pendingTicks = ticks;
    }

    private void beginPending() {
        if (pendingPosition == null) return;
        Vec3 position = pendingPosition, velocity = pendingVelocity;
        int ticks = pendingTicks;
        pendingPosition = pendingVelocity = null;
        Vec3 start = position(1);
        Vec3 tangent = velocity((double) elapsed / duration);
        from = start;
        to = position;
        fromVelocity = tangent;
        toVelocity = velocity;
        duration = Math.max(1, ticks);
        previousElapsed = elapsed = 0;
    }

    public void tick() {
        beginPending();
        previousElapsed = elapsed;
        elapsed = Math.min(duration, elapsed + 1);
    }

    public Vec3 position(float partialTick) {
        double t = (previousElapsed + (elapsed - previousElapsed) * partialTick) / duration;
        return hermite(from, to, fromVelocity.scale(duration), toVelocity.scale(duration), t);
    }

    private Vec3 velocity(double t) {
        if (elapsed >= duration) return toVelocity;
        return to.subtract(from).scale((-6 * t * t + 6 * t) / duration)
                .add(fromVelocity.scale(3 * t * t - 4 * t + 1))
                .add(toVelocity.scale(3 * t * t - 2 * t));
    }

    public static Vec3 hermite(Vec3 from, Vec3 to, Vec3 fromTangent, Vec3 toTangent, double t) {
        if (t <= 0) return from;
        if (t >= 1) return to;
        double t2 = t * t, t3 = t2 * t;
        // Relative coordinates retain precision far from the world origin.
        return from.add(to.subtract(from).scale(3 * t2 - 2 * t3))
                .add(fromTangent.scale(t3 - 2 * t2 + t))
                .add(toTangent.scale(t3 - t2));
    }
}
