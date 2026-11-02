package willow.train.hexietrain;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import org.joml.Vector3d;

/**
 * 物理体生命周期：只做两件事 —— 找到子结构、给它挂/撤"永不被视距卸载"的票据。
 *
 * <p>这个类不再驱动任何物理量。驱动力来自调度台输出的转速，
 * 转向架自己把转速变成轮轨牵引力。
 */
public final class SubLevelSuspend {

    private SubLevelSuspend() {}

    /** 我们的力加载票据类型。 */
    public static final SubLevelLoadingTicketType<BlockPos> PROXY_TICKET =
            SubLevelLoadingTicketType.create(HexieTrain.id("proxy_loading"), BlockPos.CODEC);

    /** 提前触发票据类型注册。 */
    public static void init() {
        HexieTrain.LOGGER.info("[HexieTrain] 子结构票据类型已注册: {}", PROXY_TICKET.name());
    }

    /** 找到一个位置所属的物理子结构。 */
    public static ServerSubLevel containing(ServerLevel level, BlockPos pos) {
        try {
            var sub = Sable.HELPER.getContaining(level, pos);
            return sub instanceof ServerSubLevel server ? server : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 挂上"永不被视距卸载"的票据。 */
    public static boolean resume(ServerLevel level, BlockPos dispatcherPos) {
        ServerSubLevel sub = containing(level, dispatcherPos);
        if (sub == null) return false;
        try {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return false;
            return container.addForceLoadTicket(sub, PROXY_TICKET, dispatcherPos.immutable());
        } catch (Throwable t) {
            HexieTrain.LOGGER.warn("[HexieTrain][ticket] 挂票据失败: {}", t.toString());
            return false;
        }
    }

    /** 撤掉票据，把物理体交还给世界。 */
    public static boolean suspend(ServerLevel level, BlockPos dispatcherPos) {
        ServerSubLevel sub = containing(level, dispatcherPos);
        if (sub == null) return false;
        try {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return false;
            return container.removeForceLoadTicket(sub, PROXY_TICKET, dispatcherPos.immutable());
        } catch (Throwable t) {
            HexieTrain.LOGGER.warn("[HexieTrain][ticket] 撤票据失败: {}", t.toString());
            return false;
        }
    }

    /** 子结构在世界里的中心（诊断用）。 */
    public static Vec3 worldCenter(ServerLevel level, BlockPos dispatcherPos) {
        ServerSubLevel sub = containing(level, dispatcherPos);
        if (sub == null) return Vec3.atCenterOf(dispatcherPos);
        try {
            var p = sub.logicalPose().position();
            return new Vec3(p.x, p.y, p.z);
        } catch (Throwable t) {
            return Vec3.atCenterOf(dispatcherPos);
        }
    }

    /** 兼容旧调用：现在没有锚点缓存了。 */
    public static void clearAnchorCache(BlockPos pos) {
    }

    /** 上一 tick 影子车位置（按调度台坐标）。 */
    private static final java.util.Map<BlockPos, net.minecraft.world.phys.Vec3> LAST_ANCHOR =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 让物理体跟随影子车。
     *
     * <p>速度伺服而不是瞬移：目标速度 = 影子车这一 tick 的位移 × 20（格/秒），
     * 用 addLinearAndAngularVelocity 把刚体速度拉过去。这样物理引擎仍然参与碰撞与姿态。
     */
    public static void followTrain(ServerLevel level, BlockPos dispatcherPos,
                                   com.simibubi.create.content.trains.entity.Train train) {
        if (train == null || train.carriages.isEmpty()) return;
        ServerSubLevel sub = containing(level, dispatcherPos);
        if (sub == null) return;

        net.minecraft.world.phys.Vec3 cur;
        try {
            cur = train.carriages.get(0).getLeadingPoint().getPosition(train.graph);
        } catch (Throwable t) {
            return;
        }
        if (cur == null) return;

        net.minecraft.world.phys.Vec3 last = LAST_ANCHOR.put(dispatcherPos.immutable(), cur);
        if (last == null) return;

        org.joml.Vector3d target = new org.joml.Vector3d(
                (cur.x - last.x) * 20.0, (cur.y - last.y) * 20.0, (cur.z - last.z) * 20.0);

        try {
            var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(sub);
            if (handle == null || !handle.isValid()) return;
            org.joml.Vector3d current = handle.getLinearVelocity(new org.joml.Vector3d());
            handle.addLinearAndAngularVelocity(
                    target.sub(current), new org.joml.Vector3d());
        } catch (Throwable ignored) {
        }
    }
    /**
     * 取「某个局部点在子结构里的世界速度」，单位 m/s。
     *
     * <p>实现照抄 simulated 的速度传感器（VelocitySensorBlockEntity#getGlobalVelocity）：
     * 把同一个局部点分别用"当前姿态"和"上一 tick 姿态"变换到世界坐标，相减再乘 20。
     *
     * <p>与 {@code latestLinearVelocity} / {@code RigidBodyHandle.getLinearVelocity()} 的区别：
     * 后者是**质心线速度**，不含旋转贡献；本方法测的是该点**实际怎么动**，
     * 转向、过弯、车体自转都被包含在内。
     *
     * @param localPos 子结构坐标系里的方块位置（就是方块自己的 worldPosition）
     */
    public static Vector3d velocityAt(ServerLevel level, BlockPos localPos) {
        ServerSubLevel sub = containing(level, localPos);
        if (sub == null) return new Vector3d();
        try {
            Vector3d local = JOMLConversion.toJOML(localPos.getCenter());
            return sub.logicalPose().transformPosition(local, new Vector3d())
                    .sub(sub.lastPose().transformPosition(local, new Vector3d()))
                    .mul(20.0);
        } catch (Throwable t) {
            return new Vector3d();
        }
    }
}