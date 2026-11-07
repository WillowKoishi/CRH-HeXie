package willow.train.hexie.compat.create;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.HashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import willow.train.hexie.vehicle.control.CtcsControl.Code;

/** Per-direction, per-boundary observations. No writes to signal occupancy or reservations. */
public final class CreateSignalAspects {
    public record Lamp(UUID boundary, boolean primary, double distanceM, Code code) {}
    private record Frame(long tick, List<Lamp> lamps) {}
    private static final Map<MinecraftServer, Observations> FRAMES = new WeakHashMap<>();
    private CreateSignalAspects() {}

    static void publish(ServerLevel level, UUID train, List<Lamp> lamps) {
        var observations = FRAMES.computeIfAbsent(level.getServer(), ignored -> new Observations());
        observations.publish(level.getServer().getTickCount(), train, lamps);
    }

    public static Code read(SignalBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level)) return Code.UNKNOWN;
        var boundary = be.getSignal();
        if (boundary == null) return Code.UNKNOWN;
        boolean primary = boundary.blockEntities.getFirst().containsKey(be.getBlockPos());
        if (!primary && !boundary.blockEntities.getSecond().containsKey(be.getBlockPos())) return Code.UNKNOWN;
        var observations = FRAMES.get(level.getServer());
        return observations == null ? Code.UNKNOWN
                : observations.read(boundary.id, primary, level.getServer().getTickCount());
    }

    private static boolean nearer(Lamp candidate, Lamp current) {
        return current == null || candidate.distanceM() < current.distanceM()
                || candidate.distanceM() == current.distanceM() && candidate.code().ordinal() < current.code().ordinal();
    }

    private static final class BoundaryObservations {
        final UUID boundary;
        final Map<UUID, Lamp> primary = new HashMap<>();
        final Map<UUID, Lamp> secondary = new HashMap<>();
        boolean queuedForRemoval;

        BoundaryObservations(UUID boundary) { this.boundary = boundary; }
        Map<UUID, Lamp> direction(boolean first) { return first ? primary : secondary; }
        boolean isEmpty() { return primary.isEmpty() && secondary.isEmpty(); }
    }

    /** Values do not retain the server: the outer weak key owns this observation lifetime. */
    private static final class Observations {
        final Map<UUID, Frame> frames = new HashMap<>();
        final Map<UUID, BoundaryObservations> boundaries = new HashMap<>();
        final List<BoundaryObservations> emptied = new ArrayList<>();
        long cleanupTick = Long.MIN_VALUE;

        void publish(long now, UUID train, List<Lamp> lamps) {
            expire(now);
            var previous = frames.remove(train);
            if (previous != null) withdraw(train, previous);
            // An empty frame immediately retracts this train, including a second publication in the same tick.
            if (lamps.isEmpty()) return;
            var frame = new Frame(now, List.copyOf(lamps));
            frames.put(train, frame);
            for (var lamp : frame.lamps()) {
                var boundary = boundaries.computeIfAbsent(lamp.boundary(), BoundaryObservations::new);
                var contributions = boundary.direction(lamp.primary());
                // A route can cross a boundary more than once; only its nearest contribution can win.
                if (nearer(lamp, contributions.get(train))) contributions.put(train, lamp);
            }
        }

        Code read(UUID boundary, boolean primary, long now) {
            var observations = boundaries.get(boundary);
            if (observations == null) return Code.UNKNOWN;
            Lamp nearest = null;
            for (var contribution : observations.direction(primary).entrySet()) {
                var frame = frames.get(contribution.getKey());
                // Reads also expire logically when no train has published to run physical cleanup.
                if (frame == null || now - frame.tick() > 2) continue;
                var lamp = contribution.getValue();
                if (nearer(lamp, nearest)) nearest = lamp;
            }
            return nearest == null ? Code.UNKNOWN : nearest.code();
        }

        private void expire(long now) {
            if (cleanupTick == now) return;
            cleanupTick = now;
            for (var iterator = frames.entrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next();
                if (now - entry.getValue().tick() <= 2) continue;
                withdraw(entry.getKey(), entry.getValue());
                iterator.remove();
            }
            // Keep emptied buckets until the next cleanup so the usual empty/full pair reuses its maps.
            for (var boundary : emptied) {
                boundary.queuedForRemoval = false;
                if (boundary.isEmpty()) boundaries.remove(boundary.boundary, boundary);
            }
            emptied.clear();
        }

        private void withdraw(UUID train, Frame frame) {
            for (var lamp : frame.lamps()) {
                var boundary = boundaries.get(lamp.boundary());
                if (boundary == null) continue;
                boundary.direction(lamp.primary()).remove(train);
                if (boundary.isEmpty() && !boundary.queuedForRemoval) {
                    boundary.queuedForRemoval = true;
                    emptied.add(boundary);
                }
            }
        }
    }

    public static SignalState visibleState(SignalBlockEntity be, SignalState nativeState) {
        if (be.getLevel() == null || !be.getLevel().isClientSide()
                || !(be instanceof CreateSignalAspect aspect)) return nativeState;
        return visibleState(aspect.hexie$ctcsCode(), nativeState);
    }

    public static SignalState visibleState(Code code, SignalState nativeState) {
        // Actual occupancy/forced red is never painted permissive by an approaching train's projection.
        if (nativeState == SignalState.RED || nativeState == SignalState.INVALID) return nativeState;
        return switch (code) {
            case HU -> SignalState.RED;
            case U, LU -> SignalState.YELLOW;
            case L, L2, L3, L4, L5 -> SignalState.GREEN;
            case UNKNOWN -> nativeState;
        };
    }
}
