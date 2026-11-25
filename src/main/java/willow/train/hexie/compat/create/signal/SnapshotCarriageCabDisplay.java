package willow.train.hexie.compat.create.signal;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.motion.AssembledVehicleCabControl;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.core.api.cab.CabDisplayState;
import willow.train.hexie.core.api.cab.CabDisplayStateNbt;
import willow.train.hexie.core.api.cab.CabState;
import willow.train.hexie.core.api.cab.DmiState.Mode;

// split from SnapshotCarriageEntity.java
public final class SnapshotCarriageCabDisplay {
    private final SnapshotCarriageEntity carriageEntity;

    public SnapshotCarriageCabDisplay(SnapshotCarriageEntity carriageEntity) { this.carriageEntity = carriageEntity; }

    public CabDisplayState cabDisplayState = CabDisplayState.speedOnly(0);
    public CabDisplayState simpleCabDisplayState = CabDisplayState.speedOnly(0);
    private record CabDisplayViews(CabDisplayState normal, CabDisplayState simple) {}
    private Map<BlockPos, CabDisplayViews> cabDisplayStates = Map.of();
    public double displaySpeedKmh() { return cabDisplayState.speedKmh(); }
    public CabDisplayState cabDisplayState() { return cabDisplayState; }
    public CabDisplayState cabDisplayState(BlockPos position) {
        var views = cabDisplayStates.get(position);
        if (views != null) return cabControls(position).simple() ? views.simple() : views.normal();
        return cabControls(position).simple() ? simpleCabDisplayState : cabDisplayState;
    }
    public Map<BlockPos, CabState> cabControlStates = Map.of();
    public CabState cabControls(BlockPos position) {
        return cabControlStates.getOrDefault(position, CabState.NEUTRAL);
    }

    public CompoundTag cabDisplayFrame(AssembledVehicleMotion owner, CabDisplayState base) {
        var frame = owner.cabFrame(carriageEntity.getUUID());
        var states = owner.cabStates(carriageEntity.getUUID());
        for (var value : frame.getList("Modules", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            var key = new AssembledVehicleCabControl.ControlKey(carriageEntity.getUUID(), BlockPos.of(entry.getLong("Position")));
            var state = states.get(key);
            if (state == null) continue;
            var normal = CreateCabDisplaySource.read(owner, key, state, base);
            entry.put("Display", CabDisplayStateNbt.writeProjection(normal));
            if (state.simple()) {
                var simple = CreateCabDisplaySource.readSimple(owner, key, normal);
                if (simple != normal) entry.put("SimpleDisplay", CabDisplayStateNbt.writeProjection(simple));
            }
        }
        return frame;
    }

    public void readCabDisplayFrames(CompoundTag controls) {
        var displays = new HashMap<BlockPos, CabDisplayViews>();
        for (var value : controls.getList("Modules", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            var position = BlockPos.of(entry.getLong("Position"));
            if (!cabControlStates.containsKey(position) || !entry.contains("Display", Tag.TAG_COMPOUND)) continue;
            var normal = CabDisplayStateNbt.readProjection(cabDisplayState, entry.getCompound("Display"));
            var simple = entry.contains("SimpleDisplay", Tag.TAG_COMPOUND)
                    ? CabDisplayStateNbt.readProjection(cabDisplayState, entry.getCompound("SimpleDisplay")) : normal;
            displays.put(position, new CabDisplayViews(normal, simple));
        }
        // A cab without its own projection must not inherit another cab's timetable route.
        cabControlStates.forEach((position, state) -> displays.computeIfAbsent(position, ignored -> {
            var route = HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable();
            var mode = state.automatic() ? Mode.AUTOMATIC
                    : Mode.MANUAL;
            var normal = HexieBridges.cabControl().trainDisplayReadings().withRoute(cabDisplayState, mode, route, "");
            var simple = state.automatic() ? HexieBridges.cabControl().trainDisplayReadings().withRoute(
                    normal, Mode.MANUAL, route) : normal;
            return new CabDisplayViews(normal, simple);
        }));
        cabDisplayStates = Map.copyOf(displays);
    }
}
