package willow.train.hexie.compat.create.signal;

import com.simibubi.create.content.trains.entity.TrainStatus;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.content.trains.schedule.Schedule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import willow.train.hexie.compat.create.motion.AssembledVehicleCabControl;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

// split from CreateConsoleTimetable.java
public final class CreateSharedCabTimetables {
    private CreateSharedCabTimetables() {}

    private static final Map<AssembledVehicleMotion, Map<UUID, Set<UUID>>> CONFLICT_NOTICES = new WeakHashMap<>();

    public static boolean hasConflict(AssembledVehicleMotion motion, UUID member) {
        var registries = motion.level().registryAccess();
        CompoundTag first = null;
        for (var execution : motion.cabTimetables(member).values()) {
            if (!execution.hasTimetable()) continue;
            var definition = execution.definition(registries);
            if (first == null) first = definition;
            else if (!first.equals(definition)) return true;
        }
        return false;
    }

    /** A physical connection preserves all schedules and pauses distinct definitions. */
    public static void afterCoupling(AssembledVehicleMotion motion, UUID member) { checkConflict(motion, member); }

    public static boolean checkConflict(AssembledVehicleMotion motion, UUID member) {
        boolean conflicting = hasConflict(motion, member);
        var executions = motion.cabTimetables(member).values();
        executions.forEach(execution -> execution.conflict(conflicting));
        executions.forEach(execution -> execution.markSynchronized(motion.level().registryAccess()));
        var train = motion.compatibility() == null ? null : motion.compatibility().train(member);
        if (train == null) return conflicting;
        // The bridge replaces native train identities after processing a successful connection.
        UUID consist = motion.runtime().state(member).consist().stream().min(UUID::compareTo).orElse(member);
        if (!conflicting) {
            var previousNotices = CONFLICT_NOTICES.get(motion);
            if (previousNotices != null) previousNotices.remove(consist);
            return false;
        }
        var notices = CONFLICT_NOTICES.computeIfAbsent(motion, unused -> new HashMap<>());
        var notified = notices.computeIfAbsent(consist, unused -> new HashSet<>());
        var owner = train.getOwner(motion.level());
        for (var player : motion.level().players()) {
            boolean onTrain = player.getRootVehicle() instanceof SnapshotCarriageEntity carriage
                    && motion.owns(carriage.getUUID()) && motion.runtime().connected(member, carriage.getUUID());
            if ((!onTrain && player != owner) || !notified.add(player.getUUID())) continue;
            player.displayClientMessage(CreateLang.translateDirect("train.status", train.name)
                    .withStyle(ChatFormatting.GOLD), false);
            new TrainStatus.StatusMessage(Component.literal(" - ").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable("create.train.status.hexie.multiple_timetables")
                            .withStyle(style -> style.withColor(0xFFD3B4)))).displayToPlayer(player);
        }
        return true;
    }

    /** Select one execution for this tick; external pause/resume/progress edits take precedence. */
    public static CreateCabTimetable prepareShared(AssembledVehicleMotion motion, UUID member) {
        var registries = motion.level().registryAccess();
        var selected = motion.selectedCabTimetable(member);
        var executions = motion.cabTimetables(member).values();
        var changed = executions.stream().filter(cab -> cab.externallyChanged(registries))
                .findFirst().orElse(null);
        if (changed != null) {
            var previousDefinition = changed.synchronizedDefinition();
            boolean replacement = !changed.definition(registries).equals(previousDefinition);
            if (replacement) changed.clearConflictForEdit();
            for (var target : executions) {
                if (target == changed) continue;
                if (replacement || target.definition(registries).equals(previousDefinition) || !target.hasTimetable())
                    target.synchronizeFrom(changed, registries, target.ownsItem());
            }
            changed.markSynchronized(registries);
        }
        if (checkConflict(motion, member)) return null;
        var source = changed != null && changed.hasTimetable() ? changed : selected != null && selected.hasTimetable() ? selected
                : executions.stream().filter(CreateCabTimetable::hasTimetable).findFirst().orElse(null);
        if (source != null) synchronize(motion, source);
        return source != null && selected != null ? selected : source;
    }

    public static void synchronize(AssembledVehicleMotion motion, CreateCabTimetable source) {
        var registries = motion.level().registryAccess();
        var definition = source.definition(registries);
        for (var target : motion.cabTimetables(source.key().carriage()).values())
            if (!target.hasTimetable() || target.definition(registries).equals(definition))
                target.synchronizeFrom(source, registries);
    }

    public static void setPaused(AssembledVehicleMotion motion, CreateCabTimetable source, boolean paused) {
        if (checkConflict(motion, source.key().carriage())) return;
        source.runtime.paused = paused;
        synchronize(motion, source);
    }

    /** During a conflict, eject only the clicked definition; replacement still publishes one shared table. */
    public static List<ItemStack> exchangeShared(AssembledVehicleMotion motion, CreateCabTimetable source,
                                                  Schedule next, HolderLookup.Provider registries) {
        var all = List.copyOf(motion.cabTimetables(source.key().carriage()).values());
        var definition = source.definition(registries);
        boolean separate = next == null && hasConflict(motion, source.key().carriage());
        var affected = all.stream().filter(cab -> !separate || cab.definition(registries).equals(definition)).toList();
        var returned = new ArrayList<ItemStack>();
        for (var cab : affected) {
            if (cab.ownsItem()) returned.add(CreateConsoleTimetable.timetableItem(Schedule.fromTag(registries, cab.timetable(registries)), registries));
            cab.replace(null, registries);
        }
        source.replace(next, registries);
        if (next != null) synchronize(motion, source);
        if (!checkConflict(motion, source.key().carriage())) prepareShared(motion, source.key().carriage());
        for (var cab : motion.cabTimetables(source.key().carriage()).values()) {
            if (!(motion.level().getEntity(cab.key().carriage()) instanceof SnapshotCarriageEntity entity)
                    || entity.getContraption() == null) continue;
            var info = entity.getContraption().getBlocks().get(cab.key().position());
            if (info != null) entity.setBlock(info.pos(), cab.withBlockData(info, registries));
        }
        return returned;
    }

    /** Use the canonical pending modules even when no route or motion owner can be restored. */
    public static List<ItemStack> exchangePendingShared(CompatibilityTrain train, AssembledVehicleCabControl.ControlKey sourceKey,
                                                Schedule next, ServerLevel level) {
        var registries = level.registryAccess();
        var modules = train.pendingCabModules();
        var executions = new java.util.LinkedHashMap<AssembledVehicleCabControl.ControlKey, CreateCabTimetable>();
        modules.forEach((key, module) -> {
            var data = module.contains("TimetableExecution") ? module.getCompound("TimetableExecution")
                    : CreateConsoleTimetableStorage.storedBlockData(level, key, CreateConsoleTimetableStorage.capturedCabData(level, key));
            executions.put(key, CreateCabTimetable.read(key, registries, data));
        });
        var source = executions.get(sourceKey);
        if (source == null) throw new IllegalArgumentException("Unknown pending timetable console");
        var definition = source.definition(registries);
        boolean separate = next == null && distinctTimetables(executions.values(), registries);
        var returned = new ArrayList<ItemStack>();
        for (var cab : executions.values()) {
            if (separate && !cab.definition(registries).equals(definition)) continue;
            if (cab.ownsItem()) returned.add(CreateConsoleTimetable.timetableItem(Schedule.fromTag(registries, cab.timetable(registries)), registries));
            cab.replace(null, registries);
        }
        source.replace(next, registries);
        boolean conflict = distinctTimetables(executions.values(), registries);
        executions.values().forEach(cab -> cab.conflict(conflict));
        if (!conflict) {
            var shared = next != null ? source : executions.values().stream().filter(CreateCabTimetable::hasTimetable)
                    .findFirst().orElse(null);
            if (shared != null) executions.values().forEach(cab -> cab.synchronizeFrom(shared, registries));
        }
        executions.forEach((key, cab) -> {
            modules.get(key).put("TimetableExecution", cab.write(registries));
            CreateConsoleTimetableStorage.savePendingBlockData(train, level, key, cab);
        });
        train.refreshPendingMotionCopies(level);
        return returned;
    }

    public static boolean distinctTimetables(Iterable<CreateCabTimetable> executions, HolderLookup.Provider registries) {
        CompoundTag first = null;
        for (var cab : executions) {
            if (!cab.hasTimetable()) continue;
            var definition = cab.definition(registries);
            if (first == null) first = definition;
            else if (!first.equals(definition)) return true;
        }
        return false;
    }
}
