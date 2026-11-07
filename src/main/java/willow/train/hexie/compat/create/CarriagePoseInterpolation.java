package willow.train.hexie.compat.create;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/** Plays confirmed poses on their server timeline, with a small reserve for packet jitter. */
public final class CarriagePoseInterpolation<T> {
    private static final int SAMPLE_INTERVAL = 3;
    private static final int BUFFER_TICKS = SAMPLE_INTERVAL * 2;
    private static final int MAX_SAMPLES = 32;

    public record Sample<T>(long tick, Vec3 position, Vec3 velocity, Quaternionfc rotation, T payload) {}
    public record Window<T>(Sample<T> from, Sample<T> to, float fraction) {}
    public record Span(long fromTick, long toTick, float fraction) {}

    private final ArrayList<Sample<T>> samples = new ArrayList<>();
    private List<Sample<T>> rendered = List.of();
    private double cursor, previousCursor;
    private double playbackRate = 1;
    private boolean started;
    private long idleRebase = Long.MIN_VALUE;
    private int waitingTicks;

    public boolean initialized() { return !samples.isEmpty(); }

    public boolean accept(Vec3 position, Vec3 velocity, long sampleTick) {
        return accept(position, velocity, new Quaternionf(), sampleTick, null);
    }

    /** Arrival changes queued samples only; the current rendered tick stays immutable. */
    public boolean accept(Vec3 position, Vec3 velocity, Quaternionfc rotation, long sampleTick, T payload) {
        var next = new Sample<T>(sampleTick, position, velocity, new Quaternionf(rotation), payload);
        if (samples.isEmpty()) {
            samples.add(next);
            cursor = previousCursor = sampleTick;
            rendered = List.of(next);
            return true;
        }
        var latest = samples.getLast();
        if (sampleTick < latest.tick) return false;
        if (sampleTick - latest.tick > BUFFER_TICKS && cursor >= latest.tick && latest.velocity.lengthSqr() < 1e-12) {
            // Identical stopped frames are not broadcast. A first changed frame follows
            // the last server sampling interval, not a movement spread over the whole idle.
            idleRebase = sampleTick - SAMPLE_INTERVAL;
            samples.clear();
            samples.add(new Sample<>(idleRebase, latest.position, Vec3.ZERO, latest.rotation, latest.payload));
            started = false;
            waitingTicks = 0;
            playbackRate = 1;
        }
        if (sampleTick == latest.tick) samples.set(samples.size() - 1, next);
        else samples.add(next);
        // Retain the playback anchor and real elapsed time after a very long outage.
        // Dropping detail must not compress the remaining distance into three ticks.
        while (samples.size() > MAX_SAMPLES) samples.remove(1);
        return true;
    }

    public void tick() {
        if (!initialized()) return;
        if (idleRebase != Long.MIN_VALUE) { cursor = idleRebase; idleRebase = Long.MIN_VALUE; }
        previousCursor = cursor;
        double available = samples.getLast().tick - cursor;
        if (!started && (available >= BUFFER_TICKS || ++waitingTicks >= BUFFER_TICKS && available > 0)) started = true;
        if (started) {
            double desiredRate = available < SAMPLE_INTERVAL ? .9
                    : Math.min(1.25, 1 + Math.max(0, available - BUFFER_TICKS - SAMPLE_INTERVAL) * .02);
            playbackRate += Math.clamp(desiredRate - playbackRate, -.025, .025);
            cursor += Math.min(available, playbackRate);
        }
        int first = 0;
        while (first + 1 < samples.size() && samples.get(first + 1).tick <= previousCursor) first++;
        int last = first;
        while (last + 1 < samples.size() && samples.get(last).tick < cursor) last++;
        rendered = List.copyOf(samples.subList(first, last + 1));
        while (samples.size() > 1 && samples.get(1).tick <= cursor) samples.removeFirst();
    }

    public Window<T> window(float partialTick) {
        double time = previousCursor + (cursor - previousCursor) * Math.clamp(partialTick, 0, 1);
        var from = rendered.getFirst();
        for (int i = 1; i < rendered.size(); i++) {
            var to = rendered.get(i);
            if (time <= to.tick)
                return new Window<>(from, to, (float) Math.clamp((time - from.tick) / (to.tick - (double) from.tick), 0, 1));
            from = to;
        }
        return new Window<>(from, from, 1);
    }

    public Vec3 position(float partialTick) {
        return position(window(partialTick));
    }

    public Span span(float partialTick) {
        var window = window(partialTick);
        return new Span(window.from.tick, window.to.tick, window.fraction);
    }

    /** The same confirmed segment is used for rendering and first-contact reconstruction. */
    public static Vec3 position(Window<?> window) {
        var from = window.from;
        var to = window.to;
        Vec3 chord = to.position.subtract(from.position);
        double length = chord.length();
        if (length < 1e-10) return from.position;
        double ticks = to.tick - (double) from.tick;
        Vec3 a = boundedTangent(from.velocity.scale(ticks), chord, length);
        Vec3 b = boundedTangent(to.velocity.scale(ticks), chord, length);
        // Keep the projection along a segment monotone, including braking/reversal.
        double alpha = a.dot(chord) / (length * length), beta = b.dot(chord) / (length * length);
        double magnitude = Math.sqrt(alpha * alpha + beta * beta);
        if (magnitude > 3) { a = a.scale(3 / magnitude); b = b.scale(3 / magnitude); }
        return hermite(from.position, to.position, a, b, window.fraction);
    }

    private static Vec3 boundedTangent(Vec3 tangent, Vec3 chord, double length) {
        if (tangent.dot(chord) < 0) return Vec3.ZERO;
        double magnitude = tangent.length();
        return magnitude > 3 * length ? tangent.scale(3 * length / magnitude) : tangent;
    }

    public Quaternionf rotation(float partialTick) {
        return rotation(window(partialTick));
    }

    public static Quaternionf rotation(Window<?> window) {
        return new Quaternionf(window.from.rotation).slerp(window.to.rotation, window.fraction);
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
