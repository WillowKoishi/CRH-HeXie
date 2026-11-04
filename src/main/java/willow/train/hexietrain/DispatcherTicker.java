package willow.train.hexietrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 调度台的全局驱动器。
 *
 * <p>方块实体自己的 tick() 只在区块/子结构被正常 tick 时才调用，
 * 而我们的方块经常待在 Sable 的绘图区子结构里，不保证被 tick。
 * 所以所有逻辑都从服务器全局 tick 事件驱动。
 *
 * <p>注意：ACTIVE 是强引用集合，方块被卸载时不一定有人调 remove()，
 * 于是同一位置的旧实例会残留并继续 tick（两个副本各写一份状态）。
 * 这里每 tick 做两件事：剔除失效实例、按坐标去重。
 */
@EventBusSubscriber(modid = HexieTrain.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DispatcherTicker {

    private DispatcherTicker() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Set<TrainDispatcherBlockEntity> active = TrainDispatcherBlockEntity.activeDispatchers();
        List<TrainDispatcherBlockEntity> snapshot = new ArrayList<>(active);
        Set<Long> seen = new HashSet<>();

        for (TrainDispatcherBlockEntity be : snapshot) {
            try {
                // ① 剔除失效实例
                if (be.isRemoved() || be.getLevel() == null || !be.getLevel().isLoaded(be.getBlockPos())) {
                    active.remove(be);
                    continue;
                }
                // ② 同一坐标只保留一个实例
                long key = ((long) be.getLevel().dimension().location().hashCode() << 32)
                        ^ be.getBlockPos().asLong();
                if (!seen.add(key)) {
                    active.remove(be);
                    HexieTrain.LOGGER.info("[HexieTrain][dedupe] 移除重复调度台实例 @{}", be.getBlockPos());
                    continue;
                }
                be.serverTick();
            } catch (Throwable t) {
                HexieTrain.LOGGER.error("[HexieTrain] 调度台 tick 异常 @{}", be.getBlockPos(), t);
            }
        }
    }
}