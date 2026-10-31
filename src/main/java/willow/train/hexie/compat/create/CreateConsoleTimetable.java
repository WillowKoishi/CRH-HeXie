package willow.train.hexie.compat.create;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import willow.train.hexie.blockentity.DrivingConsoleEntity;
import willow.train.hexie.blocks.locos.CRH2DrivingConsoleBlock;

/** Right-click import only. Reading copies the item; it does not change cab mode or controls. */
@EventBusSubscriber(modid = "hexie")
public final class CreateConsoleTimetable extends MovingInteractionBehaviour {
    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof ScheduleItem)
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CRH2DrivingConsoleBlock)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        var player = event.getEntity();
        if (event.getLevel().isClientSide() || player.isSpectator() || !player.mayBuild()) return;
        var schedule = read(player, event.getHand());
        if (schedule != null && event.getLevel().getBlockEntity(event.getPos()) instanceof DrivingConsoleEntity cab) {
            cab.readTimetable(schedule.write(player.registryAccess()));
            message(player, "时刻表已读入驾驶台，组装后供本车使用");
        }
    }

    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos pos,
                                                      AbstractContraptionEntity entity) {
        var info = entity.getContraption().getBlocks().get(pos);
        if (info == null || !(info.state().getBlock() instanceof CRH2DrivingConsoleBlock)
                || !(player.getItemInHand(hand).getItem() instanceof ScheduleItem)) return false;
        if (player.level().isClientSide() || player.isSpectator() || !player.mayBuild()) return true;
        var schedule = read(player, hand);
        if (schedule == null) return true;
        if (!(entity instanceof CarriageContraptionEntity car) || car.getCarriage() == null) {
            message(player, "驾驶台尚未连接列车");
            return true;
        }
        install(car.getCarriage().train, schedule);
        message(player, "时刻表已读入本车，机控与人控共用");
        return true;
    }

    /** Consume a world-console import once, after the assembled carriage receives its train identity. */
    static void importPending(SnapshotCarriageEntity entity, Train train) {
        if (entity.level().isClientSide() || entity.getContraption() == null) return;
        for (var info : java.util.List.copyOf(entity.getContraption().getBlocks().values())) {
            if (!(info.state().getBlock() instanceof CRH2DrivingConsoleBlock) || info.nbt() == null
                    || !info.nbt().contains(DrivingConsoleEntity.PENDING_TIMETABLE)) continue;
            var data = info.nbt().copy();
            var schedule = Schedule.fromTag(entity.registryAccess(), data.getCompound(DrivingConsoleEntity.PENDING_TIMETABLE));
            // An existing train timetable wins when several preloaded consoles are assembled together.
            if (train.runtime.getSchedule() == null && !schedule.entries.isEmpty()) install(train, schedule);
            data.remove(DrivingConsoleEntity.PENDING_TIMETABLE);
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
        train.navigation.cancelNavigation();
        train.navigation.waitingForSignal = null;
        train.navigation.ticksWaitingForSignal = 0;
        train.navigation.distanceToSignal = Double.MAX_VALUE;
        train.runtime.setSchedule(schedule, false);
        if (train instanceof CompatibilityTrain own) own.automaticTarget = null;
    }

    private static void message(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }
}
