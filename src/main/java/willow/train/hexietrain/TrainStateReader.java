package willow.train.hexietrain;

import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;

/**
 * 从 Create 的 {@link Train} 里读出制动需要的全部信息。
 *
 * <p>这就是"同一结构上的信息可以被统一处理"的落地：速度、目标距离、
 * 目标类型（车站 / 被锁定的区间）全在 Train 对象上，一个方块就能读全。
 */
public final class TrainStateReader {

    /**
     * @param speedBlocksPerSecond 带符号速度（格/秒）；Create 内部是 格/tick，这里 ×20
     * @param distance             到目标的距离（格）
     * @param hasTarget            是否有制动目标
     * @param targetIsSignal       true = 区间锁定（信号），false = 车站
     */
    public record State(
            double speedBlocksPerSecond,
            double distance,
            boolean hasTarget,
            boolean targetIsSignal
    ) {}

    private TrainStateReader() {}

    public static State read(Train train) {
        if (train == null) {
            return new State(0, 0, false, false);
        }
        double speedBps = train.speed * 20.0;
        Navigation nav = train.navigation;
        if (nav == null) {
            return new State(speedBps, 0, false, false);
        }

        // 区间锁定优先：前方信号没开放，和进站用同一套制动曲线
        if (nav.waitingForSignal != null) {
            return new State(speedBps, nav.distanceToSignal, true, true);
        }
        if (nav.destination != null) {
            return new State(speedBps, nav.distanceToDestination, true, false);
        }
        return new State(speedBps, 0, false, false);
    }
}