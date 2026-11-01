package willow.train.hexietrain;

import com.simibubi.create.content.trains.entity.Carriage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 与 Create: Electro Energetics（电力学）的软兼容层。
 *
 * <p>电力学只从 Create 的车厢 contraption 里找电机/受电弓，
 * 我们的物理车没有 contraption，所以要主动把信息喂给它：
 * <pre>
 *   ((IPantographList) carriage).electroEnergetics$setElectricMotor(true);
 * </pre>
 *
 * <p>扫描范围以调度台为中心，覆盖整个子结构（子结构方块都在绘图区的同一块坐标里）。
 *
 * <p>本类<b>只会在检测到 electroenergetics 已加载时才被加载</b>，
 * 否则里面的类型引用会让类初始化失败。
 */
public final class ElectricCompat {

    /** 扫描半径：水平 16、垂直 8，足以覆盖一节车厢的电机。 */
    private static final int SCAN_H = 16;
    private static final int SCAN_V = 8;

    private ElectricCompat() {}

    /**
     * 把子结构里的电气设备登记到车厢上。
     *
     * @return true = 找到了电机
     */
    public static boolean attach(ServerLevel level, BlockPos anchor, Carriage carriage) {
        try {
            if (!(carriage instanceof com.george_vi.electroenergetics.mixin_interfaces.IPantographList list)) {
                return false;
            }
            boolean hasMotor = scanForMotor(level, anchor);
            if (hasMotor) {
                list.electroEnergetics$setElectricMotor(true);
                HexieTrain.LOGGER.info("[HexieTrain][ee] 已向电力学登记电机 @{}", anchor);
            }
            return hasMotor;
        } catch (Throwable t) {
            HexieTrain.LOGGER.warn("[HexieTrain][ee] 电力学兼容失败: {}", t.toString());
            return false;
        }
    }

    private static boolean scanForMotor(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_H; dx <= SCAN_H; dx++) {
            for (int dy = -SCAN_V; dy <= SCAN_V; dy++) {
                for (int dz = -SCAN_H; dz <= SCAN_H; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be instanceof com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlockEntity) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}