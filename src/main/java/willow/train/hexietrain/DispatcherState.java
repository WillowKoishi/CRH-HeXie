package willow.train.hexietrain;

/**
 * 调度器自身的工作状态（与"自动驾驶/玩家操控"的操控模态无关）。
 */
public enum DispatcherState {

    /** 待命：没有任务，或在站内等待时刻表放行。 */
    IDLE("待命"),

    /** 行驶中：正朝目标站开去，供应牵引力。 */
    RUNNING("行驶中"),

    /** 滑车：已进制动区，切断牵引，靠惯性与摩擦减速。 */
    COASTING("滑车"),

    /** 急刹：摩擦不够，叠加反向传动。 */
    BRAKING("急刹");

    private final String label;

    DispatcherState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}