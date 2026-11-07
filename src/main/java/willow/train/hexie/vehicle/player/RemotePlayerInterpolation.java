package willow.train.hexie.vehicle.player;

import java.util.ArrayDeque;
import net.minecraft.world.phys.Vec3;

/** Plays confirmed carriage-local positions in server-tick order without extrapolation. */
public final class RemotePlayerInterpolation {
    private static final int SAMPLE_INTERVAL = 3;
    private static final int MAX_SAMPLES = 16;
    private record Sample(Vec3 position, long tick) {}

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private Vec3 position;
    private double cursor;

    public RemotePlayerInterpolation(Vec3 position, long sampleTick) {
        this.position = position;
        cursor = sampleTick;
        samples.add(new Sample(position, sampleTick));
    }

    public Vec3 position() { return position; }

    /** Arrival changes only queued samples; the displayed tick endpoints change in tick(). */
    public void accept(Vec3 nextPosition, long sampleTick) {
        Sample latest = samples.getLast();
        if (sampleTick < latest.tick) return;
        if (sampleTick == latest.tick) {
            if (nextPosition.equals(latest.position)) return;
            if (samples.size() > 1) {
                samples.removeLast();
                samples.addLast(new Sample(nextPosition, sampleTick));
                return;
            }
        }
        if (samples.size() == 1) {
            // There is no pending movement after an idle interval or an exhausted stream.
            // Its next step should not spend that whole silent interval moving from rest.
            long start = sampleTick - Math.min(SAMPLE_INTERVAL, Math.max(1, sampleTick - latest.tick));
            samples.clear();
            samples.add(new Sample(position, start));
            cursor = start;
        }
        samples.addLast(new Sample(nextPosition, sampleTick));
        if (samples.size() > MAX_SAMPLES) {
            // A long stall can deliver many packets in one frame. Keep a bounded recent
            // path and join it from the visible position on the next simulation tick.
            while (samples.size() >= MAX_SAMPLES) samples.removeFirst();
            long start = samples.getFirst().tick - SAMPLE_INTERVAL;
            samples.addFirst(new Sample(position, start));
            cursor = start;
        }
    }

    public Vec3 tick() {
        if (samples.size() == 1) return position;
        // Normal playback advances one source tick. A queued backlog catches up gradually,
        // with at most two source ticks consumed per client tick.
        double advance = Math.clamp((samples.getLast().tick - cursor) / SAMPLE_INTERVAL, 1, 2);
        while (advance > 0 && samples.size() > 1) {
            Sample from = samples.removeFirst();
            Sample to = samples.getFirst();
            double consumed = Math.min(advance, to.tick - cursor);
            cursor += consumed;
            advance -= consumed;
            double fraction = (cursor - from.tick) / (to.tick - (double) from.tick);
            position = from.position.lerp(to.position, fraction);
            if (cursor < to.tick) samples.addFirst(from);
        }
        return position;
    }
}
