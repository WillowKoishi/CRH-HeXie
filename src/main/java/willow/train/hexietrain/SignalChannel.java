package willow.train.hexietrain;

import net.minecraft.core.Direction;

/**
 * 三个红石输出通道，对应转向架上三个必须分开的面。
 *
 * <p>为什么必须分开：Simurail 的物理转向架只认自己方块面上的红石——
 * 转向读左右两面之差，刹车读上（或下）面。一个通道没法同时表达三件事。
 */
public enum SignalChannel {

    /** 刹车：发给转向架"上表面"（倒挂时为下表面），模拟量 0~15。 */
    BRAKE(0),

    /** 左转：发给转向架"左面"。 */
    LEFT(1),

    /** 右转：发给转向架"右面"。 */
    RIGHT(2);

    public static final SignalChannel[] VALUES = values();

    private final int index;

    SignalChannel(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    /**
     * 该通道应该打在转向架的哪个面上（相对于调度台的朝向）。
     *
     * @param facing 调度台朝向（水平）
     */
    public Direction face(net.minecraft.world.level.block.state.BlockState state) {
        return switch (this) {
            case BRAKE -> Direction.UP;
            case LEFT -> state.getValue(TrainDispatcherBlock.FACING);
            case RIGHT -> state.getValue(TrainDispatcherBlock.FACING).getOpposite();
        };
    }

    // ══════════════ 数值编码 ══════════════

    /**
     * 刹车通道编码。
     *
     * <p>Simurail 会对刹车强度做 {@code Mth.square}，所以这里先开平方补偿，
     * 玩家看到的才是线性的"红石 8 ≈ 一半制动力"。
     */
    public static int encodeBrake(double strength, boolean linearize) {
        double s = clamp01(strength);
        double v = linearize ? Math.sqrt(s) : s;
        return (int) Math.round(v * 15);
    }

    /** 转向通道编码：0~15 线性。 */
    public static int encodeSteer(double amount) {
        return (int) Math.round(clamp01(Math.abs(amount)) * 15);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** 制动区的完整跨度（格）。 */
    public static final double BRAKE_HORIZON = 10.0;

    /**
     * 用"距离 ÷ 速度"算刹车强度。
     *
     * <p>性质：剩余距离越短、速度越快 → 比值越小 → 刹车越猛。
    /**
     * 刹车强度：越靠近目标站点，红石信号越强。
     *
     * <p>以距离为主（保证单调）：距离 10 格 → 0，距离 0 格 → 15。
     * 速度只在"已经完全停住"时用来区分是否需要保持刹车。
     *
     * @param distance  沿轨道剩余距离（格）
     * @param speed     当前速度（格/tick）
     * @param linearize true 时做平方根补偿（Simurail 内部会平方）
     */
    public static int brakeStrength(double distance, double speed, boolean linearize) {
        if (distance <= 0) return 15;

        double s = 1.0 - clamp01(distance / BRAKE_HORIZON);   // 单调：越近越强
        if (speed <= 1.0E-4 && distance >= 1.0) return 0;      // 停稳且离站还远：不刹

        double v = linearize ? Math.sqrt(s) : s;
        return (int) Math.round(v * 15.0);
    }

    /**
     * 多段制动：摩擦刹车压不住速度时，才逐级增加反向传动。
     *
     * <p>所需减速度 = v² / (2d)（要在剩余距离内停住需要的减速度）。
     * 摩擦制动能提供的减速度与刹车强度成正比。两者比较：
     *
     * <ul>
     *   <li>0 = 摩擦够用，不反向</li>
     *   <li>1 = 摩擦不够，半反向</li>
     *   <li>2 = 摩擦远远不够，全反向</li>
     * </ul>
     */
    public static int reverseStage(double distance, double speed, int brakeStrength) {
        if (speed <= 0.02 || distance <= 0.01) return 0;

        double needed = speed * speed / (2.0 * distance);   // 所需减速度（格/tick²）
        double frictionCap = 0.004 * Math.max(1, brakeStrength);   // 摩擦能给的减速度

        if (needed <= frictionCap) return 0;
        if (needed <= frictionCap * 2.0) return 1;
        return 2;
    }
}