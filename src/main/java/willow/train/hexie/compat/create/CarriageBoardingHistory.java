package willow.train.hexie.compat.create;

import java.util.ArrayDeque;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.vehicle.player.SupportFrame;

/** Recent authoritative poses published to clients. Used only for the first movement onto a body. */
final class CarriageBoardingHistory {
    private static final class Sample {
        final long stamp;
        final SupportFrame pose;
        long lastTick;

        Sample(long tick, SupportFrame pose) {
            this.stamp = this.lastTick = tick;
            this.pose = pose;
        }
    }
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private static final int MAX_AGE_TICKS = 100;

    void clear() { samples.clear(); }

    long record(long tick, SupportFrame pose) {
        while (!samples.isEmpty() && samples.getFirst().lastTick < tick - MAX_AGE_TICKS) samples.removeFirst();
        Sample last = samples.peekLast();
        if (last != null && last.pose.equals(pose)) {
            // A stopped carriage keeps the same network payload, however long it stands here.
            last.lastTick = tick;
            return last.stamp;
        }
        samples.addLast(new Sample(tick, pose));
        return tick;
    }

    Vec3 contact(long tick, Vec3 local, ServerPlayer player, int updateInterval) {
        // Do not let a client select an arbitrary old pass of a train. Allow for packet batching,
        // the interpolation interval and the measured round trip. The history also has a hard bound.
        int delay = 2 * updateInterval + 8 + Mth.ceil(player.connection.latency() / 50.0);
        long now = player.level().getGameTime();
        Sample previous = null;
        for (Sample sample : samples) {
            if (sample.stamp == tick) {
                if (now - sample.lastTick > Math.min(MAX_AGE_TICKS, delay)) return null;
                Vec3 end = sample.pose.world(local);
                if (previous == null || now - previous.lastTick > delay || tick - previous.lastTick > 2L * updateInterval)
                    return end;
                Vec3 start = previous.pose.world(local);
                Vec3 segment = end.subtract(start);
                if (segment.lengthSqr() < 1e-12) return end;
                // The client's interpolation and the server's tick are asynchronous. Match the
                // foot point along this broadcast interval to the last accepted player position.
                double t = player.position().subtract(start).dot(segment) / segment.lengthSqr();
                return start.add(segment.scale(Mth.clamp(t, 0, 1)));
            }
            previous = sample;
        }
        return null;
    }

    boolean carryToPresent(ServerPlayer player, long tick, Vec3 local, Vec3 current) {
        for (Sample sample : samples) {
            if (sample.stamp >= tick && !CarriagePlayerSupport.carryBoardingPlayer(player, sample.pose.world(local)))
                return false;
        }
        return CarriagePlayerSupport.carryBoardingPlayer(player, current);
    }
}
