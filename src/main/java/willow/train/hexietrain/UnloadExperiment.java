package willow.train.hexietrain;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelTicketInfo;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

/**
 * 【实验代码 · EXPERIMENT · EXP-UNLOAD】
 *
 * <p>验证一件事：<b>物理结构被卸载后，能不能按 UUID / 存档指针找回来，并且姿态可用。</b>
 *
 * <p>只打日志，不改任何状态。验证结论二选一：
 * <ul>
 *   <li>可行 → 抽成正式实现（点模型 + 交接）</li>
 *   <li>不可行 → <b>删掉本文件</b>，并删掉 {@code TrainDispatcherBlockEntity.serverTick()} 里那一行调用</li>
 * </ul>
 *
 * <p>日志前缀统一为 {@code [EXP-UNLOAD]}，便于事后 grep 清除。
 */
public final class UnloadExperiment {

    private UnloadExperiment() {}

    /** 每个调度器的观察状态。 */
    private static final class Obs {
        UUID uuid;
        boolean loaded;                       // 上一 tick 是否在场
        final Vector3d lastPose = new Vector3d(Double.NaN, Double.NaN, Double.NaN);
        final Vector3d lastVel = new Vector3d();
        GlobalSavedSubLevelPointer lastPointer;
        int heartbeat;
        int transitions;
    }

    private static final Map<BlockPos, Obs> OBS = new ConcurrentHashMap<>();

    /** 每 tick 调用（挂在 serverTick 里）。 */
    public static void tick(ServerLevel level, BlockPos pos, TrainDispatcherBlockEntity be) {
        try {
            Obs o = OBS.computeIfAbsent(pos.immutable(), $ -> new Obs());

            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return;

            ServerSubLevel sub = SubLevelSuspend.containing(level, pos);
            boolean nowLoaded = sub != null;

            if (nowLoaded) {
                o.uuid = sub.getUniqueId();
                Vector3d p = sub.logicalPose().position();
                o.lastPointer = sub.getLastSerializationPointer();
                var v = be.measuredVelocity();
                o.lastVel.set(v.x(), v.y(), v.z());

                if (!o.loaded && o.transitions > 0) {
                    // 恢复事件：对比卸载时的姿态
                    double dx = p.x - o.lastPose.x, dy = p.y - o.lastPose.y, dz = p.z - o.lastPose.z;
                    HexieTrain.LOGGER.info(
                            "[EXP-UNLOAD] 恢复 uuid={} pose=({},{},{}) 与卸载时差=({},{},{}) v=({},{},{})",
                            o.uuid, f(p.x), f(p.y), f(p.z), f(dx), f(dy), f(dz), f(o.lastVel.x), f(o.lastVel.y), f(o.lastVel.z));
                    o.transitions++;
                } else if (!o.loaded) {
                    o.transitions = 1;    // 首次看到，不算恢复
                }
                if (!o.loaded) o.lastPose.set(p);
                o.loaded = true;
            } else {
                if (o.loaded && o.uuid != null) {
                    // 卸载事件：记录最后姿态 + 存档指针 + 卸载态里的姿态
                    Vector3d holdingPose = null;
                    try {
                        HoldingSubLevel h = container.getHoldingChunkMap().getHoldingSubLevel(o.uuid);
                        if (h != null) holdingPose = h.data().pose().position();
                        SubLevelTicketInfo info = container.getAllTickets().get(o.uuid);
                        HexieTrain.LOGGER.info(
                                "[EXP-UNLOAD] 卸载 uuid={} 最后pose=({},{},{}) v=({},{},{}) pointer={} holding={} holdingPose={}",
                                o.uuid, f(o.lastPose.x), f(o.lastPose.y), f(o.lastPose.z),
                                f(o.lastVel.x), f(o.lastVel.y), f(o.lastVel.z),
                                o.lastPointer, holdingPose != null,
                                holdingPose == null ? "-" : "(" + f(holdingPose.x) + "," + f(holdingPose.y) + "," + f(holdingPose.z) + ")");
                        o.transitions++;
                    } catch (Throwable t) {
                        HexieTrain.LOGGER.warn("[EXP-UNLOAD] 卸载时查询失败: {}", t.toString());
                    }
                }
                o.loaded = false;
            }

            // 每 100 tick 一条心跳，确认观测在跑
            if (++o.heartbeat % 100 == 0 && o.uuid != null) {
                HexieTrain.LOGGER.info("[EXP-UNLOAD] 心跳 loaded={} uuid={} pointer={}",
                        nowLoaded, o.uuid, o.lastPointer);
            }
        } catch (Throwable t) {
            HexieTrain.LOGGER.warn("[EXP-UNLOAD] 异常: {}", t.toString());
        }
    }

    private static String f(double v) {
        return Double.isNaN(v) ? "NaN" : String.format("%.2f", v);
    }
}