package willow.train.hexie.compat.create;

import java.util.List;
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
    private static final Map<MinecraftServer, Map<UUID, Frame>> FRAMES = new WeakHashMap<>();
    private CreateSignalAspects() {}

    static void publish(ServerLevel level, UUID train, List<Lamp> lamps) {
        var frames = FRAMES.computeIfAbsent(level.getServer(), ignored -> new HashMap<>());
        long now = level.getServer().getTickCount();
        frames.values().removeIf(frame -> now - frame.tick() > 2);
        frames.put(train, new Frame(now, List.copyOf(lamps)));
    }

    public static Code read(SignalBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level)) return Code.UNKNOWN;
        var boundary = be.getSignal();
        if (boundary == null) return Code.UNKNOWN;
        boolean primary = boundary.blockEntities.getFirst().containsKey(be.getBlockPos());
        if (!primary && !boundary.blockEntities.getSecond().containsKey(be.getBlockPos())) return Code.UNKNOWN;
        var frames = FRAMES.get(level.getServer());
        if (frames == null) return Code.UNKNOWN;
        long now = level.getServer().getTickCount();
        Lamp nearest = null;
        for (var frame : frames.values()) {
            if (now - frame.tick() > 2) continue;
            for (var lamp : frame.lamps()) {
                if (!lamp.boundary().equals(boundary.id) || lamp.primary() != primary) continue;
                if (nearest == null || lamp.distanceM() < nearest.distanceM()
                        || lamp.distanceM() == nearest.distanceM() && lamp.code().ordinal() < nearest.code().ordinal())
                    nearest = lamp;
            }
        }
        return nearest == null ? Code.UNKNOWN : nearest.code();
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
