package willow.train.hexie.compat.create;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
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
        message(player, schedule == null ? "时刻表已取出" : "时刻表已放入驾驶台，组装后供本车使用");
    }

    @SubscribeEvent
    public static void dropStoredTimetable(BlockDropsEvent event) {
        if (!(event.getBlockEntity() instanceof CabTimetableHost cab)) return;
        var data = cab.timetable();
        if (data.isEmpty()) return;
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
        if (execution != null) {
            if (train instanceof CompatibilityTrain own) bindStored(own, motion);
            var stored = execution.timetable(registries);
            if (!inserting && stored.isEmpty()) return false;
            previous = stored.isEmpty() ? ItemStack.EMPTY : timetableItem(Schedule.fromTag(registries, stored), registries);
            execution.replace(schedule, registries);
            entity.setBlock(pos, execution.withBlockData(info, registries));
        } else {
            var module = train instanceof CompatibilityTrain own ? own.pendingCabModule(cab) : null;
            var blockData = module != null && module.contains("TimetableExecution") ? module.getCompound("TimetableExecution") : info.nbt();
            var stored = blockData == null ? new CompoundTag() : blockData.getCompound(CabTimetableHost.PENDING_TIMETABLE);
            if (!inserting && stored.isEmpty()) return false;
            previous = exchangeCab(train, cab, stored, schedule, data -> {
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
        message(player, !inserting ? "时刻表已取出" : "时刻表已放入驾驶台");
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
        var returned = previous.isEmpty() ? ItemStack.EMPTY : timetableItem(Schedule.fromTag(registries, previous), registries);
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
            message(player, "时刻表没有可读取的条目");
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

    private static void message(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }
}
