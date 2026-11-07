package willow.train.hexie.compat.create;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
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
    private Timeline timeline;
    private long idleRebase = Long.MIN_VALUE;

    public CarriagePoseInterpolation() {
        timeline = new Timeline();
        timeline.members.add(this);
    }

    /** One playback clock for the currently loaded members of one physical train. */
    public static final class Timeline {
        private final LinkedHashSet<CarriagePoseInterpolation<?>> members = new LinkedHashSet<>();
        private double cursor, previousCursor;
        private double playbackRate = 1;
        private boolean initialized, started;
        private int waitingTicks;
        private long lastClientTick = Long.MIN_VALUE;

        public boolean isEmpty() { return members.isEmpty(); }

        /** Every member may call this; a client tick advances the whole train only once. */
        public void tick(long clientTick) {
            if (lastClientTick == clientTick) return;
            lastClientTick = clientTick;
            advance();
        }

        private void initialize(long sampleTick) {
            if (initialized) return;
            initialized = true;
            cursor = previousCursor = sampleTick;
        }

        private void inherit(Timeline old) {
            cursor = old.cursor;
            previousCursor = old.previousCursor;
            playbackRate = old.playbackRate;
            initialized = old.initialized;
            started = old.started;
            waitingTicks = old.waitingTicks;
            lastClientTick = old.lastClientTick;
        }

        private void advance() {
            var active = members.stream().filter(CarriagePoseInterpolation::initialized).toList();
            if (active.isEmpty()) return;
            long minimumLatest = Long.MAX_VALUE, maximumLatest = Long.MIN_VALUE;
            long requestedRebase = Long.MAX_VALUE;
            boolean allStopped = true;
            for (var member : active) {
                var latest = member.samples.getLast();
                minimumLatest = Math.min(minimumLatest, latest.tick);
                maximumLatest = Math.max(maximumLatest, latest.tick);
                allStopped &= latest.velocity.lengthSqr() < 1e-12;
                if (member.idleRebase != Long.MIN_VALUE)
                    requestedRebase = Math.min(requestedRebase, member.idleRebase);
            }
            // A moving member cannot play past any other member's confirmed data.
            // Once every member has stopped, consume staggered final stop frames;
            // unchanged stopped frames are intentionally not broadcast again.
            long horizon = allStopped ? maximumLatest : minimumLatest;
            if (requestedRebase != Long.MAX_VALUE && requestedRebase <= horizon) {
                cursor = Math.max(cursor, requestedRebase);
                started = false;
                waitingTicks = 0;
                playbackRate = 1;
                active.forEach(member -> member.idleRebase = Long.MIN_VALUE);
            }
            previousCursor = cursor;
            double available = Math.max(0, horizon - cursor);
            if (!started && (available >= BUFFER_TICKS || ++waitingTicks >= BUFFER_TICKS && available > 0)) started = true;
            if (started) {
                double desiredRate = available < SAMPLE_INTERVAL ? .9
                        : Math.min(1.25, 1 + Math.max(0, available - BUFFER_TICKS - SAMPLE_INTERVAL) * .02);
                playbackRate += Math.clamp(desiredRate - playbackRate, -.025, .025);
                cursor += Math.min(available, playbackRate);
            }
            // Freeze all members together, even when their entity ticks arrive in
            // different orders. Packet receipt never mutates this rendered window.
            active.forEach(CarriagePoseInterpolation::freezeWindow);
        }
    }

    /** Joining an existing group adopts its clock; splitting into an empty group preserves time. */
    public void attach(Timeline next) {
        Objects.requireNonNull(next);
        if (timeline == next) return;
        boolean inherit = next.isEmpty() || !next.initialized;
        if (inherit) next.inherit(timeline);
        timeline.members.remove(this);
        timeline = next;
        next.members.add(this);
        if (!inherit && initialized()) freezeWindow();
    }

    /** Release the train group while retaining this carrier's independent playback state. */
    public void detach() { attach(new Timeline()); }

    public boolean initialized() { return !samples.isEmpty(); }

    public boolean accept(Vec3 position, Vec3 velocity, long sampleTick) {
        return accept(position, velocity, new Quaternionf(), sampleTick, null);
    }

    /** Arrival changes queued samples only; the current rendered tick stays immutable. */
    public boolean accept(Vec3 position, Vec3 velocity, Quaternionfc rotation, long sampleTick, T payload) {
        var next = new Sample<T>(sampleTick, position, velocity, new Quaternionf(rotation), payload);
        if (samples.isEmpty()) {
            samples.add(next);
            timeline.initialize(sampleTick);
            rendered = List.of(next);
            return true;
        }
        var latest = samples.getLast();
        if (sampleTick < latest.tick) return false;
        if (sampleTick - latest.tick > BUFFER_TICKS && timeline.cursor >= latest.tick && latest.velocity.lengthSqr() < 1e-12) {
            // Identical stopped frames are not broadcast. A first changed frame follows
            // the last server sampling interval, not a movement spread over the whole idle.
            idleRebase = sampleTick - SAMPLE_INTERVAL;
            samples.clear();
            samples.add(new Sample<>(idleRebase, latest.position, Vec3.ZERO, latest.rotation, latest.payload));
        }
        if (sampleTick == latest.tick) samples.set(samples.size() - 1, next);
        else samples.add(next);
        // Retain the playback anchor and real elapsed time after a very long outage.
        // Dropping detail must not compress the remaining distance into three ticks.
        while (samples.size() > MAX_SAMPLES) samples.remove(1);
        return true;
    }

    public void tick() { timeline.advance(); }

    public void tick(long clientTick) { timeline.tick(clientTick); }

    private void freezeWindow() {
        int first = 0;
        while (first + 1 < samples.size() && samples.get(first + 1).tick <= timeline.previousCursor) first++;
        int last = first;
        while (last + 1 < samples.size() && samples.get(last).tick < timeline.cursor) last++;
        rendered = List.copyOf(samples.subList(first, last + 1));
        while (samples.size() > 1 && samples.get(1).tick <= timeline.cursor) samples.removeFirst();
    }

    public Window<T> window(float partialTick) {
        double time = timeline.previousCursor + (timeline.cursor - timeline.previousCursor) * Math.clamp(partialTick, 0, 1);
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
