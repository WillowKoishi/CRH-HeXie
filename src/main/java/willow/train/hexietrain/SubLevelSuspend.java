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

    /**
     * 取某个局部点的世界速度（m/s），照抄 simulated 的速度传感器。
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