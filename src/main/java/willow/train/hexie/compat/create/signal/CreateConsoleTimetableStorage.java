package willow.train.hexie.compat.create.signal;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.createmod.catnip.nbt.NBTHelper;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.VehicleBridge.CarriageSnapshotBlockView;
import willow.train.hexie.compat.create.motion.AssembledVehicleCabControl;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.compat.mixins.carriage.CreateCarriageAccessor;
import willow.train.hexie.core.api.cab.CabTimetableHost;

// split from CreateConsoleTimetable.java
public final class CreateConsoleTimetableStorage {
    private CreateConsoleTimetableStorage() {}

    public static CompoundTag pendingBlockData(CompoundTag previous, CreateCabTimetable execution,
                                                HolderLookup.Provider registries) {
        var data = previous == null ? new CompoundTag() : previous.copy();
        data.remove(CreateCabTimetable.EXECUTION);
        var saved = execution.write(registries);
        if (saved.contains(CabTimetableHost.PENDING_TIMETABLE))
            data.put(CabTimetableHost.PENDING_TIMETABLE, saved.getCompound(CabTimetableHost.PENDING_TIMETABLE));
        else data.remove(CabTimetableHost.PENDING_TIMETABLE);
        return data;
    }

    public static CompoundTag capturedCabData(ServerLevel level, AssembledVehicleCabControl.ControlKey key) {
        var snapshot = HexieBridges.vehicle().carriageSnapshotStore().get(level).find(key.carriage()).orElse(null);
        return snapshot == null ? null : snapshot.blocks().stream().filter(block -> block.position().equals(key.position()))
                .map(CarriageSnapshotBlockView::entityData)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    /** Update both loaded working blocks and native unloaded payloads, preserving all unrelated data. */
    public static void savePendingBlockData(CompatibilityTrain train, ServerLevel level,
                                             AssembledVehicleCabControl.ControlKey key, CreateCabTimetable execution) {
        if (level.getEntity(key.carriage()) instanceof SnapshotCarriageEntity entity && entity.getContraption() != null) {
            var info = entity.getContraption().getBlocks().get(key.position());
            if (info != null) entity.setBlock(key.position(), execution.withBlockData(info, level.registryAccess()));
        }
        int index = train.carriageIds.indexOf(key.carriage());
        if (index < 0) return;
        var payload = ((CreateCarriageAccessor) (Object) train.carriages.get(index)).hexie$getSerialisedEntity();
        if (payload == null || payload.isEmpty() || !payload.contains("WorkingState")) return;
        var working = payload.getCompound("WorkingState");
        boolean updated = false;
        for (String list : List.of("Actors", "ChangedBlocks")) {
            for (var value : working.getList(list, Tag.TAG_COMPOUND)) {
                var block = (CompoundTag) value;
                if (!key.position().equals(NBTHelper.readBlockPos(block, "Pos"))) continue;
                var previous = block.contains("BlockEntity") ? block.getCompound("BlockEntity") : capturedCabData(level, key);
                block.put("BlockEntity", pendingBlockData(previous, execution, level.registryAccess()));
                updated = true;
            }
        }
        if (updated) return;
        var snapshot = HexieBridges.vehicle().carriageSnapshotStore().get(level).find(key.carriage()).orElse(null);
        if (snapshot == null) return;
        var original = snapshot.blocks().stream().filter(block -> block.position().equals(key.position())).findFirst().orElse(null);
        if (original == null) return;
        var block = new CompoundTag();
        block.put("Pos", net.minecraft.nbt.NbtUtils.writeBlockPos(key.position()));
        block.put("State", net.minecraft.nbt.NbtUtils.writeBlockState(original.state()));
        block.put("BlockEntity", pendingBlockData(original.entityData(), execution, level.registryAccess()));
        var changed = working.getList("ChangedBlocks", Tag.TAG_COMPOUND);
        changed.add(block);
        working.put("ChangedBlocks", changed);
    }

    /** Native trains can also carry a console-owned timetable; restore only that marked execution state. */
    public static void restoreRuntime(Train train, CompoundTag data, HolderLookup.Provider registries) {
        if (!data.hasUUID("HeXieTimetableCarriage")) return;
        if (!(train.runtime instanceof CreateConsoleTimetable.Runtime)) train.runtime = new CreateConsoleTimetable.Runtime(train);
        train.runtime.read(registries, data);
    }

    public static CompoundTag withProgress(CompoundTag stored, Train train, AssembledVehicleCabControl.ControlKey cab) {
        if (stored.isEmpty() || !cab.equals(CreateConsoleTimetable.source(train)) || train.runtime.getSchedule() == null) return stored;
        var data = stored.copy();
        data.putInt("Progress", train.runtime.currentEntry);
        return data;
    }

    /** Use the same cab NBT for saves, item exchange and disassembly. */
    public static StructureBlockInfo withProgress(StructureBlockInfo info, Train train, UUID carriage) {
        if (info.nbt() == null || !info.nbt().contains(CabTimetableHost.PENDING_TIMETABLE)) return info;
        var stored = info.nbt().getCompound(CabTimetableHost.PENDING_TIMETABLE);
        var current = withProgress(stored, train, new AssembledVehicleCabControl.ControlKey(carriage, info.pos()));
        if (current.equals(stored)) return info;
        var data = info.nbt().copy();
        data.put(CabTimetableHost.PENDING_TIMETABLE, current);
        return new StructureBlockInfo(info.pos(), info.state(), data);
    }

    /** Native disassembly restores the live blocks directly; leave the current entry in its console first. */
    public static void saveProgress(Train train) {
        if (train instanceof CompatibilityTrain) return;
        var cab = CreateConsoleTimetable.source(train);
        if (cab == null) return;
        for (var carriage : train.carriages) carriage.forEachPresentEntity(entity -> {
            if (!cab.carriage().equals(entity.getUUID()) || entity.getContraption() == null) return;
            var info = entity.getContraption().getBlocks().get(cab.position());
            if (info == null) return;
            var updated = withProgress(info, train, cab.carriage());
            if (updated != info) entity.setBlock(cab.position(), updated);
        });
    }

    public static void migrateLegacy(CompatibilityTrain train, AssembledVehicleMotion motion) {
        var legacy = CreateConsoleTimetable.source(train);
        if (legacy == null) return;
        var cab = motion.cabTimetable(legacy);
        if (cab == null) return;
        cab.adoptLegacy(train, motion.level().registryAccess());
        train.runtime.discardSchedule();
        CreateNavigationState.clearSignalWait(train);
        train.clearStationStop();
        train.stationState.waypointTransitionTicks = 0;
        train.stationState.automaticRouteAvailable = false;
        train.stationState.hadSchedule = false;
    }

    public static CompoundTag legacyNavigation(CompatibilityTrain train) {
        if (train.runtime instanceof CreateConsoleTimetable.Runtime runtime && runtime.pendingLegacyNavigation != null
                && train.navigation.destination == null) return runtime.pendingLegacyNavigation.copy();
        var dimensions = new DimensionPalette();
        var route = new CompoundTag();
        route.put("Navigation", train.navigation.write(dimensions));
        dimensions.write(route);
        return route;
    }

    /** Pre-upgrade saves keep item edits in the live or serialized carriage's working blocks. */
    public static CompoundTag storedBlockData(ServerLevel level, AssembledVehicleCabControl.ControlKey key, CompoundTag fallback) {
        if (level.getEntity(key.carriage()) instanceof SnapshotCarriageEntity entity && entity.getContraption() != null) {
            var info = entity.getContraption().getBlocks().get(key.position());
            if (info != null) return info.nbt();
        }
        for (var candidate : Create.RAILWAYS.trains.values()) {
            if (!(candidate instanceof CompatibilityTrain train)) continue;
            int index = train.carriageIds.indexOf(key.carriage());
            if (index < 0) continue;
            var saved = ((CreateCarriageAccessor) (Object) train.carriages.get(index)).hexie$getSerialisedEntity();
            var working = saved.getCompound("WorkingState");
            for (String list : java.util.List.of("Actors", "ChangedBlocks")) {
                for (var value : working.getList(list, Tag.TAG_COMPOUND)) {
                    var block = (CompoundTag) value;
                    if (key.position().equals(NBTHelper.readBlockPos(block, "Pos"))
                            && (block.contains("BlockEntity") || list.equals("ChangedBlocks")))
                        return block.getCompound("BlockEntity");
                }
            }
        }
        return fallback;
    }
}
