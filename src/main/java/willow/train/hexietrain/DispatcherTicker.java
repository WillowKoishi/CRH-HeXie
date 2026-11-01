package willow.train.hexietrain;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 调度台的全局驱动器。
 *
 * <p>方块实体自身的 {@code tick()} 只在区块/子结构被正常 tick 时才调用，
 * 而我们的方块经常待在 Sable 的绘图区子结构里，不保证被 tick。
 * 所以所有逻辑都从服务器全局 tick 事件驱动 —— 这也符合"方块不能当大脑"的原则。
 */
@EventBusSubscriber(modid = HexieTrain.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class DispatcherTicker {

    private DispatcherTicker() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (TrainDispatcherBlockEntity be : TrainDispatcherBlockEntity.activeDispatchers()) {
            try {
                be.serverTick();
            } catch (Throwable t) {
                HexieTrain.LOGGER.error("[HexieTrain] 调度台 tick 异常 @{}", be.getBlockPos(), t);
            }
        }
    }
}