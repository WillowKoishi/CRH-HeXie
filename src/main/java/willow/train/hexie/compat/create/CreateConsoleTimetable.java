package willow.train.hexie.compat.create;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;
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

/** Right-click timetable exchange. The backend owns the schedule; cab mode and handles are unchanged. */
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
        ItemStack previous;
        boolean storedInConsole = info.nbt() != null && info.nbt().contains(CabTimetableHost.PENDING_TIMETABLE);
        if (storedInConsole) {
            // Another preloaded cab may still hold its own item while the train has a timetable.
            // Keep that item accessible at its original console instead of losing it on assembly.
            previous = exchangePending(info.nbt().getCompound(CabTimetableHost.PENDING_TIMETABLE), schedule, data -> {
                var updated = info.nbt().copy();
                if (data.isEmpty()) updated.remove(CabTimetableHost.PENDING_TIMETABLE);
                else updated.put(CabTimetableHost.PENDING_TIMETABLE, data);
                entity.setBlock(pos, new StructureBlockInfo(pos, info.state(), updated));
            }, player.registryAccess());
        } else {
            if (!(entity instanceof CarriageContraptionEntity car) || car.getCarriage() == null
                    || car.getCarriage().train == null) {
                message(player, "驾驶台尚未连接列车");
                return true;
            }
            var train = car.getCarriage().train;
            if (!inserting && train.runtime.getSchedule() == null) return true;
            previous = exchangeTrain(train, schedule, player.registryAccess());
        }
        exchangeItems(player, hand, inserting, previous);
        message(player, !inserting ? "时刻表已取出" : storedInConsole
                ? "时刻表已替换在驾驶台中" : "时刻表已装入本车，机控与人控共用");
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

    static ItemStack exchangeTrain(Train train, Schedule next, HolderLookup.Provider registries) {
        var returned = train.runtime.returnSchedule(registries);
        if (next != null) install(train, next);
        if (train instanceof CompatibilityTrain own) {
            own.automaticTarget = null;
            own.waypointTransitionTicks = 0;
            own.displayRoute = willow.train.hexie.vehicle.control.TrainDisplayReadings.Route.unavailable();
        }
        return returned;
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

    /** Consume a world-console import once, after the assembled carriage receives its train identity. */
    static void importPending(SnapshotCarriageEntity entity, Train train) {
        if (entity.level().isClientSide() || entity.getContraption() == null) return;
        for (var info : java.util.List.copyOf(entity.getContraption().getBlocks().values())) {
            if (!supports(info.state().getBlock()) || info.nbt() == null
                    || !info.nbt().contains(CabTimetableHost.PENDING_TIMETABLE)) continue;
            var data = info.nbt().copy();
            var schedule = Schedule.fromTag(entity.registryAccess(), data.getCompound(CabTimetableHost.PENDING_TIMETABLE));
            // Import only the item actually installed. Other consoles retain their stored items.
            if (train.runtime.getSchedule() != null || schedule.entries.isEmpty()) continue;
            install(train, schedule);
            data.remove(CabTimetableHost.PENDING_TIMETABLE);
            entity.setBlock(info.pos(), new StructureBlockInfo(info.pos(), info.state(), data));
        }
    }

    private static Schedule read(Player player, InteractionHand hand) {
        var schedule = ScheduleItem.getSchedule(player.registryAccess(), player.getItemInHand(hand));
        if (schedule == null || schedule.entries.isEmpty()) {
            message(player, "时刻表没有可读取的条目");
            return null;
        }
        return schedule;
    }

    private static void install(Train train, Schedule schedule) {
        // A new timetable replaces the old route request, never a change of driving mode.
        CreateNavigationState.cancel(train, CreateNavigationState.Reason.TIMETABLE_REPLACED);
        train.runtime.setSchedule(schedule, false);
        if (train instanceof CompatibilityTrain own) own.automaticTarget = null;
    }

    private static void message(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }
}
