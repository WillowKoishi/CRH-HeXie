package willow.train.hexie.compat.create;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainStatus;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import willow.train.hexie.api.cab.CabTimetableHost;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.createmod.catnip.nbt.NBTHelper;
import willow.train.hexie.mixins.mixin.CreateCarriageAccessor;

/** Each console owns its item. Create runs the timetable on behalf of that console. */
@EventBusSubscriber(modid = "hexie")
public final class CreateConsoleTimetable extends MovingInteractionBehaviour {
    private static final Map<AssembledVehicleMotion, Map<UUID, Set<UUID>>> CONFLICT_NOTICES = new WeakHashMap<>();

    static boolean hasConflict(AssembledVehicleMotion motion, UUID member) {
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
    static void afterCoupling(AssembledVehicleMotion motion, UUID member) { checkConflict(motion, member); }

    static boolean checkConflict(AssembledVehicleMotion motion, UUID member) {
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
    static CreateCabTimetable prepareShared(AssembledVehicleMotion motion, UUID member) {
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

    static void synchronize(AssembledVehicleMotion motion, CreateCabTimetable source) {
        var registries = motion.level().registryAccess();
        var definition = source.definition(registries);
        for (var target : motion.cabTimetables(source.key().carriage()).values())
            if (!target.hasTimetable() || target.definition(registries).equals(definition))
                target.synchronizeFrom(source, registries);
    }

    static void setPaused(AssembledVehicleMotion motion, CreateCabTimetable source, boolean paused) {
        if (checkConflict(motion, source.key().carriage())) return;
        source.runtime.paused = paused;
        synchronize(motion, source);
    }

    /** During a conflict, eject only the clicked definition; replacement still publishes one shared table. */
    static List<ItemStack> exchangeShared(AssembledVehicleMotion motion, CreateCabTimetable source,
                                                  Schedule next, HolderLookup.Provider registries) {
        var all = List.copyOf(motion.cabTimetables(source.key().carriage()).values());
        var definition = source.definition(registries);
        boolean separate = next == null && hasConflict(motion, source.key().carriage());
        var affected = all.stream().filter(cab -> !separate || cab.definition(registries).equals(definition)).toList();
        var returned = new ArrayList<ItemStack>();
        for (var cab : affected) {
            if (cab.ownsItem()) returned.add(timetableItem(Schedule.fromTag(registries, cab.timetable(registries)), registries));
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
    static List<ItemStack> exchangePendingShared(CompatibilityTrain train, AssembledVehicleMotion.ControlKey sourceKey,
                                                Schedule next, ServerLevel level) {
        var registries = level.registryAccess();
        var modules = train.pendingCabModules();
        var executions = new java.util.LinkedHashMap<AssembledVehicleMotion.ControlKey, CreateCabTimetable>();
        modules.forEach((key, module) -> {
            var data = module.contains("TimetableExecution") ? module.getCompound("TimetableExecution")
                    : storedBlockData(level, key, capturedCabData(level, key));
            executions.put(key, CreateCabTimetable.read(key, registries, data));
        });
        var source = executions.get(sourceKey);
        if (source == null) throw new IllegalArgumentException("Unknown pending timetable console");
        var definition = source.definition(registries);
        boolean separate = next == null && distinctTimetables(executions.values(), registries);
        var returned = new ArrayList<ItemStack>();
        for (var cab : executions.values()) {
            if (separate && !cab.definition(registries).equals(definition)) continue;
            if (cab.ownsItem()) returned.add(timetableItem(Schedule.fromTag(registries, cab.timetable(registries)), registries));
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
            savePendingBlockData(train, level, key, cab);
        });
        train.refreshPendingMotionCopies(level);
        return returned;
    }

    private static boolean distinctTimetables(Iterable<CreateCabTimetable> executions, HolderLookup.Provider registries) {
        CompoundTag first = null;
        for (var cab : executions) {
            if (!cab.hasTimetable()) continue;
            var definition = cab.definition(registries);
            if (first == null) first = definition;
            else if (!first.equals(definition)) return true;
        }
        return false;
    }

    private static CompoundTag pendingBlockData(CompoundTag previous, CreateCabTimetable execution,
                                                HolderLookup.Provider registries) {
        var data = previous == null ? new CompoundTag() : previous.copy();
        data.remove(CreateCabTimetable.EXECUTION);
        var saved = execution.write(registries);
        if (saved.contains(CabTimetableHost.PENDING_TIMETABLE))
            data.put(CabTimetableHost.PENDING_TIMETABLE, saved.getCompound(CabTimetableHost.PENDING_TIMETABLE));
        else data.remove(CabTimetableHost.PENDING_TIMETABLE);
        return data;
    }

    private static CompoundTag capturedCabData(ServerLevel level, AssembledVehicleMotion.ControlKey key) {
        var snapshot = willow.train.hexie.vehicle.assembly.CarriageSnapshotStore.get(level).find(key.carriage()).orElse(null);
        return snapshot == null ? null : snapshot.blocks().stream().filter(block -> block.position().equals(key.position()))
                .map(willow.train.hexie.vehicle.assembly.CarriageSnapshot.Block::entityData)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    /** Update both loaded working blocks and native unloaded payloads, preserving all unrelated data. */
    private static void savePendingBlockData(CompatibilityTrain train, ServerLevel level,
                                             AssembledVehicleMotion.ControlKey key, CreateCabTimetable execution) {
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
        var snapshot = willow.train.hexie.vehicle.assembly.CarriageSnapshotStore.get(level).find(key.carriage()).orElse(null);
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
    public static boolean supports(Block block) {
        if (block instanceof IBE<?> ibe) return CabTimetableHost.class.isAssignableFrom(ibe.getBlockEntityClass());
        return block instanceof EntityBlock entityBlock
                && entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) instanceof CabTimetableHost;
    }

    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (!accepts(player, event.getHand())
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof CabTimetableHost cab)) return;
        if (event.getItemStack().isEmpty() && cab.timetable().isEmpty()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide() || player.isSpectator() || !player.mayBuild()) return;
        var schedule = event.getItemStack().isEmpty() ? null : read(player, event.getHand());
        if (!event.getItemStack().isEmpty() && schedule == null) return;
        var previous = exchangePending(cab.timetable(), schedule, cab::readTimetable, player.registryAccess());
        exchangeItems(player, event.getHand(), schedule != null, previous);
        exchangeMessage(player, schedule != null);
    }

    @SubscribeEvent
    public static void dropStoredTimetable(BlockDropsEvent event) {
        if (!(event.getBlockEntity() instanceof CabTimetableHost cab)) return;
        var data = cab.timetable();
        if (!CreateCabTimetable.ownsStoredItem(data)) return;
        var registries = event.getLevel().registryAccess();
        var item = timetableItem(Schedule.fromTag(registries, data), registries);
        var pos = event.getPos();
        event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, item));
    }

    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos,
                                                      AbstractContraptionEntity entity) {
        if (entity.getContraption() == null) return false;
        var info = entity.getContraption().getBlocks().get(pos);
        if (info == null || !supports(info.state().getBlock()) || !accepts(player, hand)) return false;
        if (player.level().isClientSide() || player.isSpectator() || !player.mayBuild()) return true;
        boolean inserting = !player.getItemInHand(hand).isEmpty();
        var schedule = inserting ? read(player, hand) : null;
        if (inserting && schedule == null) return true;
        var train = entity instanceof CarriageContraptionEntity car && car.getCarriage() != null
                ? car.getCarriage().train : null;
        var cab = new AssembledVehicleMotion.ControlKey(entity.getUUID(), pos);
        var registries = player.registryAccess();
        var motion = entity instanceof SnapshotCarriageEntity snapshot ? snapshot.motion() : null;
        var execution = motion == null ? null : motion.cabTimetable(cab);
        ItemStack previous;
        List<ItemStack> additional = List.of();
        if (execution != null) {
            if (train instanceof CompatibilityTrain own) bindStored(own, motion);
            var stored = execution.timetable(registries);
            if (!inserting && stored.isEmpty()) return false;
            var returned = exchangeShared(motion, execution, schedule, registries);
            previous = returned.isEmpty() ? ItemStack.EMPTY : returned.getFirst();
            additional = returned.size() < 2 ? List.of() : returned.subList(1, returned.size());
        } else {
            var module = train instanceof CompatibilityTrain own ? own.pendingCabModule(cab) : null;
            var blockData = module != null && module.contains("TimetableExecution") ? module.getCompound("TimetableExecution") : info.nbt();
            var stored = blockData == null ? new CompoundTag() : blockData.getCompound(CabTimetableHost.PENDING_TIMETABLE);
            if (!inserting && stored.isEmpty()) return false;
            if (module != null && train instanceof CompatibilityTrain own && player.level() instanceof ServerLevel server) {
                var returned = exchangePendingShared(own, cab, schedule, server);
                previous = returned.isEmpty() ? ItemStack.EMPTY : returned.getFirst();
                additional = returned.size() < 2 ? List.of() : returned.subList(1, returned.size());
            } else previous = exchangeCab(train, cab, stored, schedule, data -> {
                var updated = info.nbt() == null ? new CompoundTag() : info.nbt().copy();
                updated.remove(CreateCabTimetable.EXECUTION);
                if (data.isEmpty()) updated.remove(CabTimetableHost.PENDING_TIMETABLE);
                else updated.put(CabTimetableHost.PENDING_TIMETABLE, data);
                if (module != null) {
                    var saved = new CompoundTag();
                    if (!data.isEmpty()) saved.put(CabTimetableHost.PENDING_TIMETABLE, data.copy());
                    module.put("TimetableExecution", saved);
                }
                entity.setBlock(pos, new StructureBlockInfo(pos, info.state(), updated));
            }, registries);
        }
        exchangeItems(player, hand, inserting, previous);
        for (var item : additional) player.getInventory().placeItemBackInInventory(item);
        if (!additional.isEmpty()) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        exchangeMessage(player, inserting);
        return true;
    }

    private static boolean accepts(Player player, InteractionHand hand) {
        var held = player.getItemInHand(hand);
        if (held.getItem() instanceof ScheduleItem) return true;
        var other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        // Let a timetable in the other hand reach its own interaction instead of first extracting the old one.
        return held.isEmpty() && !(player.getItemInHand(other).getItem() instanceof ScheduleItem);
    }

    static ItemStack exchangePending(CompoundTag previous, Schedule next, Consumer<CompoundTag> commit,
                                     HolderLookup.Provider registries) {
        var returned = !CreateCabTimetable.ownsStoredItem(previous) ? ItemStack.EMPTY
                : timetableItem(Schedule.fromTag(registries, previous), registries);
        var replacement = next == null ? new CompoundTag() : next.write(registries);
        commit.accept(replacement);
        return returned;
    }

    static ItemStack exchangeCab(Train train, AssembledVehicleMotion.ControlKey cab, CompoundTag previous,
                                 Schedule next, Consumer<CompoundTag> commit, HolderLookup.Provider registries) {
        var returned = exchangePending(withProgress(previous, train, cab), next, commit, registries);
        if (train != null) {
            boolean ownsExecution = cab.equals(source(train));
            if (ownsExecution) train.runtime.discardSchedule();
            if (!(train instanceof CompatibilityTrain) && next != null && train.runtime.getSchedule() == null)
                install(train, cab, next);
            if (ownsExecution && train instanceof CompatibilityTrain own) {
                own.clearStationStop();
                own.automaticRouteAvailable = false;
                own.waypointTransitionTicks = 0;
                own.displayRoute = willow.train.hexie.vehicle.control.TrainDisplayReadings.Route.unavailable();
            }
        }
        return returned;
    }

    static AssembledVehicleMotion.ControlKey source(Train train) {
        return train != null && train.runtime instanceof Runtime runtime ? runtime.cab : null;
    }

    /** Native trains can also carry a console-owned timetable; restore only that marked execution state. */
    public static void restoreRuntime(Train train, CompoundTag data, HolderLookup.Provider registries) {
        if (!data.hasUUID("HeXieTimetableCarriage")) return;
        if (!(train.runtime instanceof Runtime)) train.runtime = new Runtime(train);
        train.runtime.read(registries, data);
    }

    private static CompoundTag withProgress(CompoundTag stored, Train train, AssembledVehicleMotion.ControlKey cab) {
        if (stored.isEmpty() || !cab.equals(source(train)) || train.runtime.getSchedule() == null) return stored;
        var data = stored.copy();
        data.putInt("Progress", train.runtime.currentEntry);
        return data;
    }

    /** Use the same cab NBT for saves, item exchange and disassembly. */
    static StructureBlockInfo withProgress(StructureBlockInfo info, Train train, UUID carriage) {
        if (info.nbt() == null || !info.nbt().contains(CabTimetableHost.PENDING_TIMETABLE)) return info;
        var stored = info.nbt().getCompound(CabTimetableHost.PENDING_TIMETABLE);
        var current = withProgress(stored, train, new AssembledVehicleMotion.ControlKey(carriage, info.pos()));
        if (current.equals(stored)) return info;
        var data = info.nbt().copy();
        data.put(CabTimetableHost.PENDING_TIMETABLE, current);
        return new StructureBlockInfo(info.pos(), info.state(), data);
    }

    /** Native disassembly restores the live blocks directly; leave the current entry in its console first. */
    public static void saveProgress(Train train) {
        if (train instanceof CompatibilityTrain) return;
        var cab = source(train);
        if (cab == null) return;
        for (var carriage : train.carriages) carriage.forEachPresentEntity(entity -> {
            if (!cab.carriage().equals(entity.getUUID()) || entity.getContraption() == null) return;
            var info = entity.getContraption().getBlocks().get(cab.position());
            if (info == null) return;
            var updated = withProgress(info, train, cab.carriage());
            if (updated != info) entity.setBlock(cab.position(), updated);
        });
    }

    private static ItemStack timetableItem(Schedule schedule, HolderLookup.Provider registries) {
        var item = AllItems.SCHEDULE.asStack();
        item.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(registries));
        return item;
    }

    /** Commit the actual-hand exchange only after its destination accepted the timetable. */
    private static void exchangeItems(Player player, InteractionHand hand, boolean inserting, ItemStack returned) {
        if (inserting) {
            var held = player.getItemInHand(hand);
            held.shrink(1);
            player.setItemInHand(hand, held.isEmpty() ? ItemStack.EMPTY : held);
        }
        if (!returned.isEmpty()) {
            if (player.getItemInHand(hand).isEmpty()) player.setItemInHand(hand, returned);
            else player.getInventory().placeItemBackInInventory(returned);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    /** Bind execution without moving the item out of its console or restarting an existing runtime. */
    static void bindStored(SnapshotCarriageEntity entity, Train train) {
        if (entity.level().isClientSide() || entity.getContraption() == null) return;
        if (train instanceof CompatibilityTrain own) {
            if (own.bridge != null) bindStored(own, own.bridge.motion());
            return;
        }
        if (train.runtime.getSchedule() != null) return;
        for (var info : entity.getContraption().getBlocks().values()) {
            if (!supports(info.state().getBlock()) || info.nbt() == null
                    || !info.nbt().contains(CabTimetableHost.PENDING_TIMETABLE)) continue;
            var schedule = Schedule.fromTag(entity.registryAccess(), info.nbt().getCompound(CabTimetableHost.PENDING_TIMETABLE));
            if (schedule.entries.isEmpty()) continue;
            install(train, new AssembledVehicleMotion.ControlKey(entity.getUUID(), info.pos()), schedule);
            return;
        }
    }

    /** The motion owner can restore every controller even before its carriage entity materializes. */
    static void bindStored(CompatibilityTrain train, AssembledVehicleMotion motion) {
        migrateLegacy(train, motion);
        for (var cab : motion.cabTimetables(train.carriageIds.getFirst()).values()) cab.bind(train);
    }

    static void invalidateNavigation(CompatibilityTrain train) {
        var executions = new java.util.HashSet<CreateCabTimetable>();
        if (train.bridge != null && !train.carriageIds.isEmpty())
            executions.addAll(train.bridge.motion().cabTimetables(train.carriageIds.getFirst()).values());
        if (train.currentCabTimetable != null) executions.add(train.currentCabTimetable);
        executions.forEach(CreateCabTimetable::invalidateNavigation);
        invalidateNavigationObservations(train);
    }

    static void invalidateNavigationObservations(CompatibilityTrain train) {
        if (train.runtime instanceof Runtime runtime && runtime.pendingLegacyNavigation != null) {
            runtime.pendingLegacyNavigation = null;
            if (runtime.state == ScheduleRuntime.State.IN_TRANSIT) runtime.transitInterrupted();
        }
        CreateNavigationState.clearSignalWait(train);
        train.automaticRouteAvailable = false;
        train.controlObservationsReady = false;
        train.waypointTransitionTicks = 0;
        train.waypointTransitionActive = false;
        train.displayRoute = willow.train.hexie.vehicle.control.TrainDisplayReadings.Route.unavailable();
    }

    static void migrateLegacy(CompatibilityTrain train, AssembledVehicleMotion motion) {
        var legacy = source(train);
        if (legacy == null) return;
        var cab = motion.cabTimetable(legacy);
        if (cab == null) return;
        cab.adoptLegacy(train, motion.level().registryAccess());
        train.runtime.discardSchedule();
        CreateNavigationState.clearSignalWait(train);
        train.clearStationStop();
        train.waypointTransitionTicks = 0;
        train.automaticRouteAvailable = false;
        train.hadSchedule = false;
    }

    static CompoundTag legacyNavigation(CompatibilityTrain train) {
        if (train.runtime instanceof Runtime runtime && runtime.pendingLegacyNavigation != null
                && train.navigation.destination == null) return runtime.pendingLegacyNavigation.copy();
        var dimensions = new DimensionPalette();
        var route = new CompoundTag();
        route.put("Navigation", train.navigation.write(dimensions));
        dimensions.write(route);
        return route;
    }

    /** Pre-upgrade saves keep item edits in the live or serialized carriage's working blocks. */
    static CompoundTag storedBlockData(ServerLevel level, AssembledVehicleMotion.ControlKey key, CompoundTag fallback) {
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

    private static Schedule read(Player player, InteractionHand hand) {
        var schedule = ScheduleItem.getSchedule(player.registryAccess(), player.getItemInHand(hand));
        if (schedule == null || schedule.entries.isEmpty()) {
            AllSoundEvents.DENY.playOnServer(player.level(), player.blockPosition(), 1, 1);
            player.displayClientMessage(CreateLang.translateDirect("schedule.no_stops"), true);
            return null;
        }
        return schedule;
    }

    private static void install(Train train, AssembledVehicleMotion.ControlKey cab, Schedule schedule) {
        // A new timetable replaces the old route request, never a change of driving mode.
        CreateNavigationState.cancel(train, CreateNavigationState.Reason.TIMETABLE_REPLACED);
        if (!(train.runtime instanceof Runtime)) train.runtime = new Runtime(train);
        train.runtime.setSchedule(schedule, false);
        ((Runtime) train.runtime).cab = cab;
        if (train instanceof CompatibilityTrain own) own.automaticRouteAvailable = false;
    }

    /** Only the source address is added to Create's existing execution state, never another item store. */
    static final class Runtime extends ScheduleRuntime {
        private AssembledVehicleMotion.ControlKey cab;
        private CompoundTag pendingLegacyNavigation;

        Runtime(Train train) { super(train); }

        @Override public void setSchedule(Schedule schedule, boolean auto) {
            super.setSchedule(schedule, auto);
            cab = null;
            pendingLegacyNavigation = null;
        }

        @Override public void discardSchedule() {
            super.discardSchedule();
            cab = null;
            pendingLegacyNavigation = null;
        }

        @Override public ItemStack returnSchedule(HolderLookup.Provider registries) {
            // Native conductor/station return paths do not own a console's physical item.
            return cab == null ? super.returnSchedule(registries) : ItemStack.EMPTY;
        }

        @Override public CompoundTag write(HolderLookup.Provider registries) {
            var data = super.write(registries);
            if (cab != null) {
                data.putUUID("HeXieTimetableCarriage", cab.carriage());
                data.putLong("HeXieTimetablePosition", cab.position().asLong());
                if (pendingLegacyNavigation != null) data.put("HeXieLegacyNavigation", pendingLegacyNavigation.copy());
            }
            return data;
        }

        @Override public void read(HolderLookup.Provider registries, CompoundTag data) {
            super.read(registries, data);
            cab = getSchedule() != null && data.hasUUID("HeXieTimetableCarriage")
                    ? new AssembledVehicleMotion.ControlKey(data.getUUID("HeXieTimetableCarriage"),
                            BlockPos.of(data.getLong("HeXieTimetablePosition"))) : null;
            pendingLegacyNavigation = cab != null && data.contains("HeXieLegacyNavigation")
                    ? data.getCompound("HeXieLegacyNavigation").copy() : null;
        }

        void keepUnresolvedNavigation(CompoundTag navigation, DimensionPalette dimensions) {
            if (cab == null || !navigation.hasUUID("Destination")) return;
            pendingLegacyNavigation = new CompoundTag();
            pendingLegacyNavigation.put("Navigation", navigation.copy());
            dimensions.write(pendingLegacyNavigation);
        }
    }

    private static void exchangeMessage(Player player, boolean inserted) {
        if (inserted) {
            AllSoundEvents.CONFIRM.playOnServer(player.level(), player.blockPosition(), 1, 1);
            player.displayClientMessage(CreateLang.translateDirect("schedule.applied_to_train")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            AllSoundEvents.playItemPickup(player);
            player.displayClientMessage(CreateLang.translateDirect("schedule.removed_from_train"), true);
        }
    }
}
