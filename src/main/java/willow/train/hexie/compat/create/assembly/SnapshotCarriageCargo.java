package willow.train.hexie.compat.create.assembly;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.common.collect.ImmutableMap;
import com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageWrapper;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.minecart.TrainCargoManager;
import com.simibubi.create.content.trains.entity.Carriage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

// split from SnapshotCarriageEntity.java
public final class SnapshotCarriageCargo {
    private final SnapshotCarriageEntity carriageEntity;

    public SnapshotCarriageCargo(SnapshotCarriageEntity carriageEntity) { this.carriageEntity = carriageEntity; }

    /** All timetable contexts observe one shared cargo clock for this physical train tick. */
    public static void prepareTimetableTick(com.simibubi.create.content.trains.entity.Train train, long gameTime) {
        for (var carriage : train.carriages)
            if (carriage.storage instanceof SnapshotCargoManager cargo) cargo.timetableTick = gameTime;
    }

    public static void preparePersistentCargo(Carriage carriage, ServerLevel level) {
        if (carriage.storage instanceof SnapshotCargoManager) return;
        var data = new CompoundTag();
        carriage.storage.write(data, level.registryAccess(), false);
        var cargo = new SnapshotCargoManager();
        cargo.read(data, level.registryAccess(), false, null);
        if (!carriage.storage.getAllItemStorages().keySet().equals(cargo.getAllItemStorages().keySet())
                || !carriage.storage.getFluids().storages.keySet().equals(cargo.getFluids().storages.keySet()))
            throw new IllegalStateException("Mounted cargo conversion lost storage entries");
        for (var entry : carriage.storage.getAllItemStorages().entrySet())
            if (entry.getValue().type != cargo.getAllItemStorages().get(entry.getKey()).type)
                throw new IllegalStateException("Mounted item storage changed type during conversion");
        for (var entry : carriage.storage.getFluids().storages.entrySet())
            if (entry.getValue().type != cargo.getFluids().storages.get(entry.getKey()).type)
                throw new IllegalStateException("Mounted fluid storage changed type during conversion");
        carriage.storage = cargo;
    }

    public void restoreMountedStorage() {
        if (carriageEntity.snapshot == null) return;
        for (var info : carriageEntity.getContraption().getBlocks().values()) {
            BlockPos world = carriageEntity.snapshot.origin().offset(info.pos());
            carriageEntity.getContraption().getStorage().unmount(carriageEntity.level(), info, world, carriageEntity.level().getBlockEntity(world));
        }
    }

    /** Only native placement sees these temporary keys; the carriage keeps its live cargo. */
    public static final class SnapshotCargoManager extends TrainCargoManager {
        private ImmutableMap<BlockPos, MountedItemStorage> disassemblyItems;
        private MountedFluidStorageWrapper disassemblyFluids;
        private long timetableTick = Long.MIN_VALUE;
        private long lastCargoTick = Long.MIN_VALUE;

        @Override public void tickIdleCargoTracker() {
            if (timetableTick != Long.MIN_VALUE) {
                if (lastCargoTick == timetableTick) return;
                lastCargoTick = timetableTick;
            }
            super.tickIdleCargoTracker();
        }

        void prepareDisassemblyView(Map<BlockPos, List<BlockPos>> groups, Map<BlockPos, BlockPos> moved) {
            disassemblyItems = relocateControllers(super.getAllItemStorages(), groups, moved);
            var fluidMap = relocateControllers(super.getFluids().storages, groups, moved);
            if (fluidMap != null) disassemblyFluids = new MountedFluidStorageWrapper(fluidMap);
        }

        private static <T> ImmutableMap<BlockPos, T> relocateControllers(Map<BlockPos, T> storages,
                Map<BlockPos, List<BlockPos>> groups, Map<BlockPos, BlockPos> moved) {
            Map<BlockPos, T> remapped = null;
            for (var move : moved.entrySet()) {
                BlockPos controller = move.getKey();
                if (!storages.containsKey(controller)) continue;
                // Per-block inventories (for example vaults) stay with their own blocks.
                // Only storage mounted exclusively on the controller follows its NBT.
                if (groups.get(controller).stream().anyMatch(pos ->
                        !pos.equals(controller) && storages.containsKey(pos))) continue;
                if (remapped == null) remapped = new HashMap<>(storages);
                remapped.put(move.getValue(), remapped.remove(controller));
            }
            return remapped == null ? null : ImmutableMap.copyOf(remapped);
        }

        void clearDisassemblyView() { disassemblyItems = null; disassemblyFluids = null; }

        @Override public ImmutableMap<BlockPos, MountedItemStorage> getAllItemStorages() {
            return disassemblyItems == null ? super.getAllItemStorages() : disassemblyItems;
        }

        @Override public MountedFluidStorageWrapper getFluids() {
            return disassemblyFluids == null ? super.getFluids() : disassemblyFluids;
        }
    }
}
