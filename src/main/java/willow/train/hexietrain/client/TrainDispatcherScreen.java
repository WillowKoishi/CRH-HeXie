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
    private static final int CELL_COUNT = 15;

    private static final int TOGGLE_X = 8;
    private static final int TOGGLE_Y = 106;
    private static final int TOGGLE_W = 100;
    private static final int TOGGLE_H = 16;
    private static final int SENS_MINUS_X = 112;
    private static final int SENS_PLUS_X = 156;
    private static final int SENS_BTN_W = 12;

    private static final int BAR_X = 38;
    private static final int BAR_Y = 21;
    private static final int BAR_W = 130;
    private static final int BAR_H = 18;

    private int marqueeTicks;

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

        // 下一站长栏
        g.fill(x + BAR_X - 1, y + BAR_Y - 1, x + BAR_X + BAR_W + 1, y + BAR_Y + BAR_H + 1, C_SLOT_BG);
        g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, C_SLOT_INNER);
        g.fill(x + BAR_X + 1, y + BAR_Y + 1, x + BAR_X + BAR_W - 1, y + BAR_Y + BAR_H - 1, 0xFFB4B4B4);

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

        // 起步模式按钮 + 灵敏度按钮
        boolean hover = isIn(mouseX, mouseY, TOGGLE_X, TOGGLE_Y, TOGGLE_W, TOGGLE_H);
        g.fill(x + TOGGLE_X, y + TOGGLE_Y, x + TOGGLE_X + TOGGLE_W, y + TOGGLE_Y + TOGGLE_H,
                hover ? C_BUTTON_HOT : C_BUTTON);
        g.renderOutline(x + TOGGLE_X, y + TOGGLE_Y, TOGGLE_W, TOGGLE_H, C_BORDER);

        drawSmallButton(g, x + SENS_MINUS_X, y + TOGGLE_Y, mouseX, mouseY);
        drawSmallButton(g, x + SENS_PLUS_X, y + TOGGLE_Y, mouseX, mouseY);
        g.fill(x + SENS_MINUS_X + SENS_BTN_W, y + TOGGLE_Y,
                x + SENS_PLUS_X, y + TOGGLE_Y + TOGGLE_H, C_SLOT_INNER);
    }

    private void drawSmallButton(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int relX = x - leftPos;
        boolean hover = isIn(mouseX, mouseY, relX, TOGGLE_Y, SENS_BTN_W, TOGGLE_H);
        g.fill(x, y, x + SENS_BTN_W, y + TOGGLE_H, hover ? C_BUTTON_HOT : C_BUTTON);
        g.renderOutline(x, y, SENS_BTN_W, TOGGLE_H, C_BORDER);
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

        // 下一站（滚轮可选；太长跑马灯）
        var be = menu.blockEntity();
        String target;
        int curEntry = menu.currentEntry();
        int sel = 0;
        if (be != null && !be.stopNames().isEmpty()) {
            sel = Math.max(0, Math.min(be.stopNames().size() - 1, menu.selectedStop()));
            target = be.stopNames().get(sel);
        } else {
            target = be == null ? "" : be.nextTargetName();
            if (target == null || target.isEmpty()) target = "（无目标）";
        }

        // 正在执行的那一条 → 白字 + 逐字扫描成黄；未确认的滚动选择 → 绿字
        boolean isCurrent = sel == curEntry && !"（无目标）".equals(target);
        boolean noTarget = "（无目标）".equals(target);
        int baseColor = (isCurrent || noTarget) ? 0xFFFFFFFF : 0xFF3FBF3F;
        int scanColor = 0xFFFFD700;

        marqueeTicks++;
        int confirmW = 18;
        int innerW = BAR_W - 8 - confirmW;
        int textW = font.width(target);
        int scroll = 0;
        if (textW > innerW) {
            int cycle = textW + 24;
        }

        // 扫描高亮位置：每 tick 前进一个字，走到末尾停 8 tick 再回到开头
        int scanLen = target.length();
        int scan = (scanLen == 0 || "（无目标）".equals(target)) ? -1 : ((marqueeTicks / 10) % (scanLen + 8));

        g.enableScissor(leftPos + BAR_X + 1, topPos + BAR_Y + 1,
                leftPos + BAR_X + BAR_W - 1 - confirmW, topPos + BAR_Y + BAR_H - 1);
        int textX = BAR_X + 4 - scroll;
        for (int i = 0; i < target.length(); i++) {
            String ch = String.valueOf(target.charAt(i));
            int color = (isCurrent && i == scan) ? scanColor : baseColor;
            g.drawString(font, ch, textX, BAR_Y + 5, color, false);
            textX += font.width(ch);
        }
        g.disableScissor();

        // 确认按钮 →]
        int bx = BAR_X + BAR_W - confirmW;
        boolean hoverConfirm = isIn(mouseX, mouseY, bx, BAR_Y, confirmW, BAR_H);
        g.fill(bx, BAR_Y + 1, bx + confirmW - 1, BAR_Y + BAR_H - 1,
                hoverConfirm ? C_BUTTON_HOT : C_BUTTON);
        g.fill(bx, BAR_Y + 1, bx + confirmW - 1, BAR_Y + 2, C_PANEL_LIGHT);
        g.fill(bx, BAR_Y + BAR_H - 2, bx + confirmW - 1, BAR_Y + BAR_H - 1, C_PANEL_DARK);
        g.fill(bx, BAR_Y + 1, bx + 1, BAR_Y + BAR_H - 1, C_PANEL_LIGHT);
        g.fill(bx + confirmW - 2, BAR_Y + 1, bx + confirmW - 1, BAR_Y + BAR_H - 1, C_PANEL_DARK);
        g.drawString(font, "\u2192]", bx + 3, BAR_Y + 5, C_TEXT, false);

        // 调度器工作状态（与右上角操控模态无关）
        // 只有装了时刻表才显示调度状态；拔掉时刻表就完全不显示
        if (menu.bound() && menu.stopCount() > 0) {
            String[] stateNames = {"待命", "行驶中", "滑车", "急刹"};
            int[] stateColors = {0xFF6E6E6E, 0xFF2E7D32, 0xFFB8860B, 0xFFB00020};
            int stIdx = Math.max(0, Math.min(stateNames.length - 1, menu.dispatcherState()));
            g.drawString(font, "调度：" + stateNames[stIdx], 8, 128, stateColors[stIdx], false);
        }
        // 三通道 + 15 格信号灯
        for (int row = 0; row < 3; row++) {
            int value = switch (row) {
                case 0 -> menu.brakeOutput();
                case 1 -> menu.leftOutput();
                default -> menu.rightOutput();
            };
            value = Math.max(0, Math.min(CELL_COUNT, value));

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

        g.drawString(font, "物品栏", 8, 137, C_TEXT_DIM, false);
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
        int count = menu.stopCount();
        if (count > 0 && isIn(x, y, BAR_X, BAR_Y, BAR_W, BAR_H)
                && minecraft != null && minecraft.gameMode != null) {
            int next = menu.selectedStop() + (scrollY < 0 ? 1 : -1);
            if (next < 0) next = count - 1;
            if (next >= count) next = 0;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 100 + next);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) (mouseX - leftPos);
        int y = (int) (mouseY - topPos);
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            if (isIn(x, y, SENS_MINUS_X, TOGGLE_Y, SENS_BTN_W, TOGGLE_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
                return true;
            }
            if (isIn(x, y, SENS_PLUS_X, TOGGLE_Y, SENS_BTN_W, TOGGLE_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2);
                return true;
            }
            // 右上角徽标 = 驾驶模式切换
            if (menu.bound() && isIn(x, y, PANEL_W - 74, 1, 70, 15)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 3);
                return true;
            }
            // 站名栏右端的 →] 确认按钮
            if (isIn(x, y, BAR_X + BAR_W - 18, BAR_Y, 18, BAR_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 200 + menu.selectedStop());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}