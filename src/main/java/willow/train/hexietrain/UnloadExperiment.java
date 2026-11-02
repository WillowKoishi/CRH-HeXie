package willow.train.hexietrain;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

/**
 * 【实验代码 · EXPERIMENT · EXP-UNLOAD】
 *
 * <p>验证两件事：
 * <ol>
 *   <li>物理结构会不会因为玩家走远而被卸载（loaded 是否变 false）</li>
 *   <li>走远之后物理是否还在算（pose 是否仍在变化、速度是否非零）</li>
 * </ol>
 *
 * <p><b>只打日志，不改任何状态。</b>结论二选一：
 * <ul>
 *   <li>结构不卸载 / 物理照常 → 点模型不需要，<b>删掉本文件</b>与 serverTick 里的调用</li>
 *   <li>结构会卸载 → 保留并扩成正式的点模型交接</li>
 * </ul>
 *
 * <p>日志前缀 {@code [EXP-UNLOAD]}，便于事后 grep 清除。
 */
public final class UnloadExperiment {

    private UnloadExperiment() {}

    private static final class Obs {
        UUID uuid;
        boolean loaded;
        boolean everLoaded;
        int beat;
        long lastTick = -1;
        long lastBeatTick = -1;
        final Vector3d lastPose = new Vector3d(Double.NaN, Double.NaN, Double.NaN);
        final Vector3d hbPose = new Vector3d(Double.NaN, Double.NaN, Double.NaN);
        final Vector3d lastVel = new Vector3d();
        GlobalSavedSubLevelPointer pointer;
        int movingBeats;      // 心跳间位置有变化的次数
        int stillBeats;       // 心跳间位置没变的次数
    }

    private static final Map<BlockPos, Obs> OBS = new ConcurrentHashMap<>();

    public static void tick(ServerLevel level, BlockPos pos, TrainDispatcherBlockEntity be) {
        try {
            Obs o = OBS.computeIfAbsent(pos.immutable(), $ -> new Obs());
            long now = level.getGameTime();

            // ── 时间跳变 = 这期间没在 tick（卸载或卡死）──
            if (o.lastTick > 0 && now - o.lastTick > 40) {
                HexieTrain.LOGGER.info("[EXP-UNLOAD] 中断 {} tick（{} → {}）",
                        now - o.lastTick, o.lastTick, now);
            }
            o.lastTick = now;

            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return;

            ServerSubLevel sub = SubLevelSuspend.containing(level, pos);
            boolean nowLoaded = sub != null;
            Vector3d pose = null;

            if (nowLoaded) {
                o.uuid = sub.getUniqueId();
                o.pointer = sub.getLastSerializationPointer();
                pose = sub.logicalPose().position();
                var v = be.measuredVelocity();
                o.lastVel.set(v.x(), v.y(), v.z());

                if (o.everLoaded && !o.loaded) {
                    double dx = pose.x - o.lastPose.x, dy = pose.y - o.lastPose.y, dz = pose.z - o.lastPose.z;
                    HexieTrain.LOGGER.info(
                            "[EXP-UNLOAD] 恢复 uuid={} pose=({},{},{}) 与离线前差=({},{},{})",
                            o.uuid, f(pose.x), f(pose.y), f(pose.z), f(dx), f(dy), f(dz));
                }
                if (!o.loaded) o.lastPose.set(pose);
                o.loaded = true;
                o.everLoaded = true;
            } else {
                if (o.loaded && o.uuid != null) {
                    HoldingSubLevel h = container.getHoldingChunkMap().getHoldingSubLevel(o.uuid);
                    Vector3d hp = h == null ? null : h.data().pose().position();
                    HexieTrain.LOGGER.info(
                            "[EXP-UNLOAD] 卸载 uuid={} 最后pose=({},{},{}) v=({},{},{}) pointer={} holding={} holdingPose={}",
                            o.uuid, f(o.lastPose.x), f(o.lastPose.y), f(o.lastPose.z),
                            f(o.lastVel.x), f(o.lastVel.y), f(o.lastVel.z), o.pointer,
                            h != null,
                            hp == null ? "-" : "(" + f(hp.x) + "," + f(hp.y) + "," + f(hp.z) + ")");
                }
                o.loaded = false;
            }

            // ── 心电图：每 20 tick（1 秒）打一次位置与速度 ──
            if (o.beat++ % 20 == 0 && o.uuid != null) {
                double dx = Double.NaN, dy = Double.NaN, dz = Double.NaN, dist = 0;
                if (nowLoaded && pose != null && !Double.isNaN(o.hbPose.x)) {
                    dx = pose.x - o.hbPose.x; dy = pose.y - o.hbPose.y; dz = pose.z - o.hbPose.z;
                    dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > 0.001) o.movingBeats++; else o.stillBeats++;
                }
                if (nowLoaded && pose != null) o.hbPose.set(pose);

                double sp = Math.sqrt(o.lastVel.x * o.lastVel.x + o.lastVel.y * o.lastVel.y + o.lastVel.z * o.lastVel.z);
                HexieTrain.LOGGER.info(
                        "[EXP-UNLOAD] 心跳 loaded={} pose=({},{},{}) Δ=({},{},{}) |Δ|={} |v|={} 动/静={}/{}",
                        nowLoaded,
                        pose == null ? "-" : f(pose.x), pose == null ? "-" : f(pose.y), pose == null ? "-" : f(pose.z),
                        f(dx), f(dy), f(dz), f(dist), f(sp),
                        o.movingBeats, o.stillBeats);
            }
        } catch (Throwable t) {
            HexieTrain.LOGGER.warn("[EXP-UNLOAD] 异常: {}", t.toString());
        }
    }

    private static String f(double v) {
        return Double.isNaN(v) ? "NaN" : String.format("%.3f", v);
    }
}