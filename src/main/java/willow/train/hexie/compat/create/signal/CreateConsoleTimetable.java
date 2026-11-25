package willow.train.hexie.compat.create.signal;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import java.util.UUID;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.motion.AssembledVehicleCabControl;
import willow.train.hexie.compat.create.motion.AssembledVehicleMotion;
import willow.train.hexie.compat.create.motion.CompatibilityTrain;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;
import willow.train.hexie.core.api.cab.CabTimetableHost;

/** Each console owns its item. Create runs the timetable on behalf of that console. */
public final class CreateConsoleTimetable extends MovingInteractionBehaviour {
    public static void restoreRuntime(Train train, CompoundTag data, HolderLookup.Provider registries) {
        CreateConsoleTimetableStorage.restoreRuntime(train, data, registries);
    }

    static StructureBlockInfo withProgress(StructureBlockInfo info, Train train, UUID carriage) {
        return CreateConsoleTimetableStorage.withProgress(info, train, carriage);
    }

    public static void saveProgress(Train train) {
        CreateConsoleTimetableStorage.saveProgress(train);
    }

    static void migrateLegacy(CompatibilityTrain train, AssembledVehicleMotion motion) {
        CreateConsoleTimetableStorage.migrateLegacy(train, motion);
    }

    static CompoundTag legacyNavigation(CompatibilityTrain train) {
        return CreateConsoleTimetableStorage.legacyNavigation(train);
    }

    static CompoundTag storedBlockData(ServerLevel level, AssembledVehicleCabControl.ControlKey key, CompoundTag fallback) {
        return CreateConsoleTimetableStorage.storedBlockData(level, key, fallback);
    }

    static boolean hasConflict(AssembledVehicleMotion motion, UUID member) {
        return CreateSharedCabTimetables.hasConflict(motion, member);
    }

    static void afterCoupling(AssembledVehicleMotion motion, UUID member) {
        CreateSharedCabTimetables.afterCoupling(motion, member);
    }

    static boolean checkConflict(AssembledVehicleMotion motion, UUID member) {
        return CreateSharedCabTimetables.checkConflict(motion, member);
    }

    static CreateCabTimetable prepareShared(AssembledVehicleMotion motion, UUID member) {
        return CreateSharedCabTimetables.prepareShared(motion, member);
    }

    static void synchronize(AssembledVehicleMotion motion, CreateCabTimetable source) {
        CreateSharedCabTimetables.synchronize(motion, source);
    }

    static void setPaused(AssembledVehicleMotion motion, CreateCabTimetable source, boolean paused) {
        CreateSharedCabTimetables.setPaused(motion, source, paused);
    }

    static List<ItemStack> exchangeShared(AssembledVehicleMotion motion, CreateCabTimetable source,
                                                  Schedule next, HolderLookup.Provider registries) {
        return CreateSharedCabTimetables.exchangeShared(motion, source, next, registries);
    }

    static List<ItemStack> exchangePendingShared(CompatibilityTrain train, AssembledVehicleCabControl.ControlKey sourceKey,
                                                Schedule next, ServerLevel level) {
        return CreateSharedCabTimetables.exchangePendingShared(train, sourceKey, next, level);
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
        var cab = new AssembledVehicleCabControl.ControlKey(entity.getUUID(), pos);
        var registries = player.registryAccess();
        var motion = entity instanceof SnapshotCarriageEntity snapshot ? snapshot.motion() : null;
        var execution = motion == null ? null : motion.cabTimetable(cab);
        ItemStack previous;
        List<ItemStack> additional = List.of();
        if (execution != null) {
            if (train instanceof CompatibilityTrain own) bindStored(own, motion);
            var stored = execution.timetable(registries);
            if (!inserting && stored.isEmpty()) return false;
            var returned = CreateSharedCabTimetables.exchangeShared(motion, execution, schedule, registries);
            previous = returned.isEmpty() ? ItemStack.EMPTY : returned.getFirst();
            additional = returned.size() < 2 ? List.of() : returned.subList(1, returned.size());
        } else {
            var module = train instanceof CompatibilityTrain own ? own.pendingCabModule(cab) : null;
            var blockData = module != null && module.contains("TimetableExecution") ? module.getCompound("TimetableExecution") : info.nbt();
            var stored = blockData == null ? new CompoundTag() : blockData.getCompound(CabTimetableHost.PENDING_TIMETABLE);
            if (!inserting && stored.isEmpty()) return false;
            if (module != null && train instanceof CompatibilityTrain own && player.level() instanceof ServerLevel server) {
                var returned = CreateSharedCabTimetables.exchangePendingShared(own, cab, schedule, server);
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

    static ItemStack exchangeCab(Train train, AssembledVehicleCabControl.ControlKey cab, CompoundTag previous,
                                 Schedule next, Consumer<CompoundTag> commit, HolderLookup.Provider registries) {
        var returned = exchangePending(CreateConsoleTimetableStorage.withProgress(previous, train, cab), next, commit, registries);
        if (train != null) {
            boolean ownsExecution = cab.equals(source(train));
            if (ownsExecution) train.runtime.discardSchedule();
            if (!(train instanceof CompatibilityTrain) && next != null && train.runtime.getSchedule() == null)
                install(train, cab, next);
            if (ownsExecution && train instanceof CompatibilityTrain own) {
                own.clearStationStop();
                own.stationState.automaticRouteAvailable = false;
                own.stationState.waypointTransitionTicks = 0;
                own.stationState.displayRoute = HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable();
            }
        }
        return returned;
    }

    static AssembledVehicleCabControl.ControlKey source(Train train) {
        return train != null && train.runtime instanceof Runtime runtime ? runtime.cab : null;
    }

    static ItemStack timetableItem(Schedule schedule, HolderLookup.Provider registries) {
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
    public static void bindStored(SnapshotCarriageEntity entity, Train train) {
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
            install(train, new AssembledVehicleCabControl.ControlKey(entity.getUUID(), info.pos()), schedule);
            return;
        }
    }

    /** The motion owner can restore every controller even before its carriage entity materializes. */
    public static void bindStored(CompatibilityTrain train, AssembledVehicleMotion motion) {
        CreateConsoleTimetableStorage.migrateLegacy(train, motion);
        for (var cab : motion.cabTimetables(train.carriageIds.getFirst()).values()) cab.bind(train);
    }

    public static void invalidateNavigation(CompatibilityTrain train) {
        var executions = new java.util.HashSet<CreateCabTimetable>();
        if (train.bridge != null && !train.carriageIds.isEmpty())
            executions.addAll(train.bridge.motion().cabTimetables(train.carriageIds.getFirst()).values());
        if (train.stationState.currentCabTimetable != null) executions.add(train.stationState.currentCabTimetable);
        executions.forEach(CreateCabTimetable::invalidateNavigation);
        invalidateNavigationObservations(train);
    }

    static void invalidateNavigationObservations(CompatibilityTrain train) {
        if (train.runtime instanceof Runtime runtime && runtime.pendingLegacyNavigation != null) {
            runtime.pendingLegacyNavigation = null;
            if (runtime.state == ScheduleRuntime.State.IN_TRANSIT) runtime.transitInterrupted();
        }
        CreateNavigationState.clearSignalWait(train);
        train.stationState.automaticRouteAvailable = false;
        train.stationState.controlObservationsReady = false;
        train.stationState.waypointTransitionTicks = 0;
        train.stationState.waypointTransitionActive = false;
        train.stationState.displayRoute = HexieBridges.cabControl().trainDisplayReadingsRoute().unavailable();
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

    private static void install(Train train, AssembledVehicleCabControl.ControlKey cab, Schedule schedule) {
        // A new timetable replaces the old route request, never a change of driving mode.
        CreateNavigationState.cancel(train, CreateNavigationState.Reason.TIMETABLE_REPLACED);
        if (!(train.runtime instanceof Runtime)) train.runtime = new Runtime(train);
        train.runtime.setSchedule(schedule, false);
        ((Runtime) train.runtime).cab = cab;
        if (train instanceof CompatibilityTrain own) own.stationState.automaticRouteAvailable = false;
    }

    /** Only the source address is added to Create's existing execution state, never another item store. */
    public static final class Runtime extends ScheduleRuntime {
        private AssembledVehicleCabControl.ControlKey cab;
        CompoundTag pendingLegacyNavigation;

        public Runtime(Train train) { super(train); }

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
                    ? new AssembledVehicleCabControl.ControlKey(data.getUUID("HeXieTimetableCarriage"),
                            BlockPos.of(data.getLong("HeXieTimetablePosition"))) : null;
            pendingLegacyNavigation = cab != null && data.contains("HeXieLegacyNavigation")
                    ? data.getCompound("HeXieLegacyNavigation").copy() : null;
        }

        public void keepUnresolvedNavigation(CompoundTag navigation, DimensionPalette dimensions) {
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
