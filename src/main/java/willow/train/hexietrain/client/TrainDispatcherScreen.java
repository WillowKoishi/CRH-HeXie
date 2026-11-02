package willow.train.hexietrain.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import willow.train.hexietrain.TrainDispatcherMenu;

/**
 * 物理火车调度器界面 —— 纯控制面板，不含玩家背包。
 *
 * <p>风格贴合机械动力：浅灰面板 + 内高光/内阴影浮雕 + 深灰文字 + 黄铜点缀。
 */
public class TrainDispatcherScreen extends AbstractContainerScreen<TrainDispatcherMenu> {

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 230;

    // ── 配色 ──
    private static final int C_BORDER      = 0xFF3D3D3D;
    private static final int C_PANEL       = 0xFFC6C6C6;
    private static final int C_PANEL_LIGHT = 0xFFDEDEDE;
    private static final int C_PANEL_DARK  = 0xFF8B8B8B;
    private static final int C_HEADER      = 0xFFB4B4B4;
    private static final int C_SLOT_BG     = 0xFF8B8B8B;
    private static final int C_SLOT_INNER  = 0xFF373737;
    private static final int C_TEXT        = 0xFF3D3D3D;
    private static final int C_TEXT_DIM    = 0xFF6E6E6E;
    private static final int C_CELL_OFF    = 0xFF8B8B8B;
    private static final int C_CELL_FRAME  = 0xFF5A5A5A;
    private static final int C_BUTTON      = 0xFFB0B0B0;
    private static final int C_BUTTON_HOT  = 0xFFC9C9C9;

    // ── 布局 ──
    private static final int[] ROW_Y = {54, 74, 94};
    private static final String[] ROW_NAMES = {"刹车", "左转", "右转"};
    private static final String[] STAGE_NAMES = {"巡航", "滑车", "反推", "强反推", "满刹"};

    private static final int CELLS_X = 86;
    private static final int CELL_W = 4;
    private static final int CELL_GAP = 1;
    /** 站名栏浏览索引（纯客户端，-1 表示跟随当前目的地）。 */
    private int browseIndex = -1;
    private static final int CELL_COUNT = 15;
    public TrainDispatcherScreen(TrainDispatcherMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    // ══════════════════ 背景 ══════════════════

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        g.fill(x, y, x + PANEL_W, y + PANEL_H, C_PANEL);
        // 标题条
        g.fill(x + 1, y + 1, x + PANEL_W - 1, y + 16, C_HEADER);
        // 内高光 / 内阴影
        g.fill(x + 1, y + 1, x + PANEL_W - 1, y + 2, C_PANEL_LIGHT);
        g.fill(x + 1, y + 1, x + 2, y + PANEL_H - 1, C_PANEL_LIGHT);
        g.fill(x + 1, y + PANEL_H - 2, x + PANEL_W - 1, y + PANEL_H - 1, C_PANEL_DARK);
        g.fill(x + PANEL_W - 2, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, C_PANEL_DARK);
        g.renderOutline(x - 1, y - 1, PANEL_W + 2, PANEL_H + 2, C_BORDER);

        // 时刻表槽
        slotWell(g, x + 13, y + 21);
        // 频率槽
        for (int row = 0; row < 3; row++) {
            slotWell(g, x + 39, y + ROW_Y[row] - 1);
            slotWell(g, x + 59, y + ROW_Y[row] - 1);
        }


        // 站名栏（时刻表右侧）
        g.fill(x + 37, y + 20, x + 169, y + 40, C_SLOT_BG);
        g.fill(x + 38, y + 21, x + 168, y + 39, C_SLOT_INNER);
        g.fill(x + 39, y + 22, x + 167, y + 38, 0xFFB4B4B4);

        // 分隔线
        g.fill(x + 8, y + 46, x + PANEL_W - 8, y + 47, C_PANEL_DARK);
        g.fill(x + 8, y + 47, x + PANEL_W - 8, y + 48, C_PANEL_LIGHT);

        // 15 格信号灯底槽
        for (int row = 0; row < 3; row++) {
            int cy = y + ROW_Y[row] + 4;
            for (int i = 0; i < CELL_COUNT; i++) {
                int cx = x + CELLS_X + i * (CELL_W + CELL_GAP);
                g.fill(cx - 1, cy - 1, cx + CELL_W + 1, cy + 9, C_CELL_FRAME);
            }
        }

        // 玩家背包格
        for (int invRow = 0; invRow < 3; invRow++) {
            for (int col = 0; col < 9; col++) {
                slotWell(g, x + 7 + col * 18, y + 145 + invRow * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotWell(g, x + 7 + col * 18, y + 203);
        }
        // 背包与上方面板的分隔
        g.fill(x + 8, y + 134, x + PANEL_W - 8, y + 135, C_PANEL_DARK);
        g.fill(x + 8, y + 135, x + PANEL_W - 8, y + 136, C_PANEL_LIGHT);
    }


    private void slotWell(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, C_SLOT_BG);
        g.fill(x + 1, y + 1, x + 17, y + 17, C_SLOT_INNER);
        g.fill(x + 2, y + 2, x + 16, y + 16, C_SLOT_BG);
    }

    // ══════════════════ 前景 ══════════════════

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int text = C_TEXT;
        int dim = C_TEXT_DIM;

        g.drawString(font, title, 8, 4, text, false);

        // 右上角 = 驾驶模式按钮（未绑定时只显示状态）
        String badge;
        if (menu.manualMode()) {
            badge = "玩家操控中";
        } else if (!menu.bound()) {
            badge = "未绑定";
        } else {
            badge = "自动驾驶中";
        }
        int badgeColor = menu.manualMode() ? 0xFFB4632A
                : (!menu.bound() ? 0xFF8B8B8B : 0xFF2E7D32);
        boolean badgeHover = isIn(mouseX, mouseY, PANEL_W - 74, 1, 70, 15);
        g.drawString(font, badge, PANEL_W - 8 - font.width(badge), 4,
                badgeHover ? 0xFF1F5FBF : badgeColor, false);


        // 站名栏 = 纯显示：滚轮浏览站点，颜色表示相对当前目的地的位置
        var be9 = menu.blockEntity();
        java.util.List<String> names9 = (be9 == null) ? java.util.List.of() : be9.stopNames();
        int dest9 = menu.currentEntry();                    // 当前目的地（时刻表游标）
        int cnt9 = names9.size();

        if (cnt9 > 0) {
            if (browseIndex < 0 || browseIndex >= cnt9) browseIndex = Math.max(0, Math.min(cnt9 - 1, dest9));
            String name9 = names9.get(browseIndex);

            int color9;
            if (browseIndex == dest9)      color9 = 0xFFFFD700;   // 正在前往 —— 黄
            else if (browseIndex > dest9)  color9 = 0xFF3FBF3F;   // 之后 —— 绿
            else                           color9 = 0xFF8B8B8B;   // 已经过 —— 灰

            int maxW9 = 168 - 42 - 4;
            String shown9 = name9 == null ? "" : name9;
            if (font.width(shown9) > maxW9) {
                while (shown9.length() > 1 && font.width(shown9 + "...") > maxW9) {
                    shown9 = shown9.substring(0, shown9.length() - 1);
                }
                shown9 = shown9 + "...";
            }
            g.drawString(font, shown9, 42, 27, color9, false);
        } else {
            String none9 = be9 != null && be9.nextTargetName() != null && !be9.nextTargetName().isEmpty()
                    ? be9.nextTargetName() : "（无目标）";
            g.drawString(font, none9, 42, 27, C_TEXT_DIM, false);
        }

        // 调度器工作状态（与右上角操控模态无关）
        // 只有装了时刻表才显示调度状态；拔掉时刻表就完全不显示
        if (menu.bound() && menu.stopCount() > 0) {
            String[] stateNames = {"待命", "行驶中", "滑车", "急刹"};
            int[] stateColors = {0xFF6E6E6E, 0xFF2E7D32, 0xFFB8860B, 0xFFB00020};
            int stIdx = Math.max(0, Math.min(stateNames.length - 1, menu.dispatcherState()));
            g.drawString(font, "调度：" + stateNames[stIdx], 8, 112, stateColors[stIdx], false);
        }
        for (int row = 0; row < 3; row++) {
            int value = switch (row) {
                case 0 -> menu.brakeOutput();
                case 1 -> menu.leftOutput();
                default -> menu.rightOutput();
            };
            value = Math.max(0, Math.min(CELL_COUNT, value));

            String[] chLabels = {"刹车", "左岔道", "右岔道"};
            g.drawString(font, chLabels[row], 2, ROW_Y[row] + 4, C_TEXT, false);
            int cy = ROW_Y[row] + 4;
            for (int i = 0; i < CELL_COUNT; i++) {
                int cx = CELLS_X + i * (CELL_W + CELL_GAP);
                boolean lit = i < value;
                int color = lit ? litColor(i) : C_CELL_OFF;
                g.fill(cx, cy, cx + CELL_W, cy + 8, color);
                if (lit) {
                    g.fill(cx, cy, cx + CELL_W, cy + 1, 0xFFFFDDDD);
                }
            }
        }

        g.drawString(font, "物品栏", 8, 136, C_TEXT_DIM, false);

        // 右下角：速度传感器读数
        String sensor = String.format("%.1f m/s", menu.sensorSpeedCenti() / 100.0);
        g.drawString(font, sensor, PANEL_W - 8 - font.width(sensor), 136, 0xFF1F5FBF, false);
    }

    /**
     * 第 i 格点亮时的颜色：R 固定 255，G/B 随序号递减，
     * 于是从左到右由"深红(带灰)"过渡到"鲜红(纯红)"。
     */
    private static int litColor(int index) {
        int gray = 90 * (CELL_COUNT - 1 - index) / (CELL_COUNT - 1);
        return 0xFFFF0000 | (gray << 8) | gray;
    }

    private static boolean isIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ══════════════════ 交互 ══════════════════
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = (int) (mouseX - leftPos);
        int y = (int) (mouseY - topPos);
        var be9 = menu.blockEntity();
        int cnt9 = be9 == null ? 0 : be9.stopNames().size();
        if (cnt9 > 0 && isIn(x, y, 38, 20, 130, 20)) {
            browseIndex += (scrollY < 0 ? 1 : -1);
            if (browseIndex < 0) browseIndex = cnt9 - 1;
            if (browseIndex >= cnt9) browseIndex = 0;
            return true;                                  // 纯本地浏览，不发包
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) (mouseX - leftPos);
        int y = (int) (mouseY - topPos);
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            // 右上角徽标 = 驾驶模式切换
            // 右上角徽标 = 驾驶模式切换
            if (menu.bound() && isIn(x, y, PANEL_W - 74, 1, 70, 15)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 3);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}