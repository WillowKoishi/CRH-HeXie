package willow.train.hexie.ui.sticker;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.sticker.*;

/**
 * Free-camera window editor entered by right-clicking a saved window's anchor block face.
 * - WASD (+ Space/Shift) moves the free camera
 * - Right-click toggles mouse pointer liberation vs camera look
 * - Left-click & drag on the 3D Up/Down/Left/Right arrow gizmos moves the window in its plane
 * - Draggable full-parameter floating panel adjusts every window parameter via buttons or direct input
 * - ESC commits and exits
 */
public final class StickerEditorScreen extends Screen {
    private static final int PANEL_W = 248;
    private static final int PANEL_H = 246;
    private static final int TITLE_H = 20;

    private int panelX = 16;
    private int panelY = 24;
    private boolean draggingPanel;
    private int dragOffX, dragOffY;
    private double lastMouseX, lastMouseY;

    // 3D Gizmo drag state
    private StickerPlaneMath.UV gizmoDragStartUv;
    private float gizmoStartOffU, gizmoStartOffV;

    private EditBox frameColorBox, glassColorBox, glassAlphaBox, thicknessBox, radiusBox, widthBox, heightBox, offUBox, offVBox;

    public StickerEditorScreen() {
        super(Component.literal("車窗自由編輯"));
    }

    @Override
    protected void init() {
        rebuildPanelWidgets();
    }

    private void rebuildPanelWidgets() {
        clearWidgets();
        StickerEntry entry = StickerToolState.editingEntry();
        if (entry == null) return;
        WindowStickerParams p = entry.window();

        int y = panelY + TITLE_H + 6;
        frameColorBox = addColorField("邊框色", String.format("#%06X", p.frameColorRgb()), y); y += 21;
        glassColorBox = addColorField("玻璃色", String.format("#%06X", p.glassColorRgb()), y); y += 21;
        glassAlphaBox = addParamRow("透明度", fmt(p.glassAlpha()), y, 0, 0.05f); y += 21;
        thicknessBox = addParamRow("邊框厚", fmt(p.frameThickness()), y, 1, 0.015625f); y += 21;
        radiusBox = addParamRow("圓角", fmt(p.cornerRadius()), y, 2, 0.03125f); y += 21;
        widthBox = addParamRow("寬度", fmt(p.width()), y, 3, 0.0625f); y += 21;
        heightBox = addParamRow("高度", fmt(p.height()), y, 4, 0.0625f); y += 21;
        offUBox = addParamRow("水平偏移", fmt(p.offsetU()), y, 5, 0.0625f); y += 21;
        offVBox = addParamRow("垂直偏移", fmt(p.offsetV()), y, 6, 0.0625f); y += 24;

        addRenderableWidget(Button.builder(Component.literal("套用數值"), b -> applyFieldsAndCommit())
                .bounds(panelX + 10, y, 108, 18).build());
        addRenderableWidget(Button.builder(Component.literal("完成 (ESC)"), b -> onClose())
                .bounds(panelX + 126, y, 112, 18).build());
    }

    private EditBox addColorField(String label, String val, int y) {
        EditBox box = new EditBox(font, panelX + 76, y, 110, 17, Component.literal(label));
        box.setValue(val);
        box.setResponder(s -> applyFieldsLive());
        addRenderableWidget(box);
        return box;
    }

    private EditBox addParamRow(String label, String val, int y, int idx, float step) {
        EditBox box = new EditBox(font, panelX + 76, y, 76, 17, Component.literal(label));
        box.setValue(val);
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal("-"), b -> stepParam(idx, -step))
                .bounds(panelX + 156, y, 20, 17).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> stepParam(idx, step))
                .bounds(panelX + 178, y, 20, 17).build());
        return box;
    }

    private void stepParam(int idx, float delta) {
        applyFieldsLive();
        var entry = StickerToolState.editingEntry();
        if (entry == null) return;
        var p = entry.window();
        var next = switch (idx) {
            case 0 -> new WindowStickerParams(p.frameColorRgb(), p.glassColorRgb(), p.glassAlpha() + delta,
                    p.frameThickness(), p.cornerRadius(), p.width(), p.height(), p.offsetU(), p.offsetV());
            case 1 -> new WindowStickerParams(p.frameColorRgb(), p.glassColorRgb(), p.glassAlpha(),
                    p.frameThickness() + delta, p.cornerRadius(), p.width(), p.height(), p.offsetU(), p.offsetV());
            case 2 -> new WindowStickerParams(p.frameColorRgb(), p.glassColorRgb(), p.glassAlpha(),
                    p.frameThickness(), p.cornerRadius() + delta, p.width(), p.height(), p.offsetU(), p.offsetV());
            case 3 -> p.withSize(p.width() + delta, p.height());
            case 4 -> p.withSize(p.width(), p.height() + delta);
            case 5 -> p.withOffset(p.offsetU() + delta, p.offsetV());
            case 6 -> p.withOffset(p.offsetU(), p.offsetV() + delta);
            default -> p;
        };
        StickerToolState.updateEditingEntry(next);
        syncTextBoxesFromState();
        commitLive();
    }

    private void applyFieldsLive() {
        var entry = StickerToolState.editingEntry();
        if (entry == null || frameColorBox == null) return;
        var cur = entry.window();
        StickerToolState.updateEditingEntry(new WindowStickerParams(
                parseColor(frameColorBox.getValue(), cur.frameColorRgb()),
                parseColor(glassColorBox.getValue(), cur.glassColorRgb()),
                parseFloat(glassAlphaBox.getValue(), cur.glassAlpha()),
                parseFloat(thicknessBox.getValue(), cur.frameThickness()),
                parseFloat(radiusBox.getValue(), cur.cornerRadius()),
                parseFloat(widthBox.getValue(), cur.width()),
                parseFloat(heightBox.getValue(), cur.height()),
                parseFloat(offUBox.getValue(), cur.offsetU()),
                parseFloat(offVBox.getValue(), cur.offsetV())));
    }

    private void applyFieldsAndCommit() {
        applyFieldsLive();
        syncTextBoxesFromState();
        commitLive();
    }

    private void syncTextBoxesFromState() {
        var entry = StickerToolState.editingEntry();
        if (entry == null || frameColorBox == null) return;
        var p = entry.window();
        frameColorBox.setValue(String.format("#%06X", p.frameColorRgb()));
        glassColorBox.setValue(String.format("#%06X", p.glassColorRgb()));
        glassAlphaBox.setValue(fmt(p.glassAlpha()));
        thicknessBox.setValue(fmt(p.frameThickness()));
        radiusBox.setValue(fmt(p.cornerRadius()));
        widthBox.setValue(fmt(p.width()));
        heightBox.setValue(fmt(p.height()));
        offUBox.setValue(fmt(p.offsetU()));
        offVBox.setValue(fmt(p.offsetV()));
    }

    private void commitLive() {
        var entry = StickerToolState.editingEntry();
        if (entry != null) HexieBridges.sync().sendStickerUpsert(entry);
    }

    @Override
    public void tick() {
        super.tick();
        if (!StickerToolState.isFreeCamActive()) {
            super.onClose();
            return;
        }
        // Only move free camera via WASD/Space/Shift when no text box is focused
        if (getFocused() instanceof EditBox box && box.isFocused()) return;
        long win = Minecraft.getInstance().getWindow().getWindow();
        double fwd = (keyDown(win, GLFW.GLFW_KEY_W) ? 1 : 0) - (keyDown(win, GLFW.GLFW_KEY_S) ? 1 : 0);
        double strafe = (keyDown(win, GLFW.GLFW_KEY_D) ? 1 : 0) - (keyDown(win, GLFW.GLFW_KEY_A) ? 1 : 0);
        double up = (keyDown(win, GLFW.GLFW_KEY_SPACE) ? 1 : 0) - (keyDown(win, GLFW.GLFW_KEY_LEFT_SHIFT) ? 1 : 0);
        if (fwd != 0 || strafe != 0 || up != 0) {
            double yawRad = Math.toRadians(StickerToolState.camYaw());
            double pitchRad = Math.toRadians(StickerToolState.camPitch());
            Vec3 look = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
            Vec3 right = new Vec3(-Math.cos(yawRad), 0, -Math.sin(yawRad));
            double speed = 0.22;
            Vec3 delta = look.scale(fwd * speed).add(right.scale(strafe * speed)).add(0, up * speed, 0);
            StickerToolState.setCamPos(StickerToolState.camPos().add(delta));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        // Right-click toggles cursor lock vs camera look mode!
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            boolean nextUnlocked = !StickerToolState.isCursorUnlocked();
            StickerToolState.setCursorUnlocked(nextUnlocked);
            long win = Minecraft.getInstance().getWindow().getWindow();
            GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR,
                    nextUnlocked ? GLFW.GLFW_CURSOR_NORMAL : GLFW.GLFW_CURSOR_DISABLED);
            return true;
        }
        if (!StickerToolState.isCursorUnlocked()) return true;

        // Check draggable floating panel title bar
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mouseX >= panelX && mouseX <= panelX + PANEL_W
                && mouseY >= panelY && mouseY <= panelY + TITLE_H) {
            draggingPanel = true;
            dragOffX = (int) mouseX - panelX;
            dragOffY = (int) mouseY - panelY;
            return true;
        }

        // Let panel widgets handle clicks inside panel bounds
        if (mouseX >= panelX && mouseX <= panelX + PANEL_W && mouseY >= panelY && mouseY <= panelY + PANEL_H) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Otherwise check 3D Up/Down/Left/Right Arrow Gizmos on the window plane!
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            var hit = pickGizmo(mouseX, mouseY);
            if (hit != StickerToolState.GizmoAxis.NONE) {
                var entry = StickerToolState.editingEntry();
                var uv = rayToWindowPlane(mouseX, mouseY);
                if (entry != null && uv != null) {
                    StickerToolState.setDraggingAxis(hit);
                    gizmoDragStartUv = uv;
                    gizmoStartOffU = entry.window().offsetU();
                    gizmoStartOffV = entry.window().offsetV();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!StickerToolState.isCursorUnlocked()) {
            double dx = mouseX - lastMouseX;
            double dy = mouseY - lastMouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            StickerToolState.setCamAngles(
                    StickerToolState.camYaw() + (float) (dx * 0.25),
                    StickerToolState.camPitch() + (float) (dy * 0.25));
            return;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (StickerToolState.draggingAxis() == StickerToolState.GizmoAxis.NONE && !draggingPanel) {
            StickerToolState.setHoveredAxis(pickGizmo(mouseX, mouseY));
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPanel) {
            panelX = Math.max(0, Math.min(width - PANEL_W, (int) mouseX - dragOffX));
            panelY = Math.max(0, Math.min(height - PANEL_H, (int) mouseY - dragOffY));
            rebuildPanelWidgets();
            return true;
        }
        var axis = StickerToolState.draggingAxis();
        if (axis != StickerToolState.GizmoAxis.NONE && gizmoDragStartUv != null) {
            var entry = StickerToolState.editingEntry();
            var uv = rayToWindowPlane(mouseX, mouseY);
            if (entry != null && uv != null) {
                float du = (float) (uv.u() - gizmoDragStartUv.u());
                float dv = (float) (uv.v() - gizmoDragStartUv.v());
                float nextU = (axis == StickerToolState.GizmoAxis.LEFT || axis == StickerToolState.GizmoAxis.RIGHT)
                        ? gizmoStartOffU + du : gizmoStartOffU;
                float nextV = (axis == StickerToolState.GizmoAxis.UP || axis == StickerToolState.GizmoAxis.DOWN)
                        ? gizmoStartOffV + dv : gizmoStartOffV;
                StickerToolState.updateEditingEntry(entry.window().withOffset(nextU, nextV));
                syncTextBoxesFromState();
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPanel) {
            draggingPanel = false;
            return true;
        }
        if (StickerToolState.draggingAxis() != StickerToolState.GizmoAxis.NONE) {
            StickerToolState.setDraggingAxis(StickerToolState.GizmoAxis.NONE);
            gizmoDragStartUv = null;
            commitLive();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private StickerPlaneMath.UV rayToWindowPlane(double mouseX, double mouseY) {
        var entry = StickerToolState.editingEntry();
        if (entry == null) return null;
        double fov = Math.toRadians(Minecraft.getInstance().options.fov().get());
        double aspect = (double) width / Math.max(1, height);
        double ndcX = (mouseX / Math.max(1, width)) * 2.0 - 1.0;
        double ndcY = 1.0 - (mouseY / Math.max(1, height)) * 2.0;
        double tanHalf = Math.tan(fov * 0.5);
        double yawRad = Math.toRadians(StickerToolState.camYaw());
        double pitchRad = Math.toRadians(StickerToolState.camPitch());
        Vec3 forward = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad)).normalize();
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = forward.cross(worldUp).normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 dir = forward.add(right.scale(ndcX * tanHalf * aspect)).add(up.scale(ndcY * tanHalf)).normalize();
        return StickerPlaneMath.intersectPlane(entry.anchorPos(), entry.facing(), StickerToolState.camPos(), dir);
    }

    private StickerToolState.GizmoAxis pickGizmo(double mouseX, double mouseY) {
        var entry = StickerToolState.editingEntry();
        if (entry == null) return StickerToolState.GizmoAxis.NONE;
        var uv = rayToWindowPlane(mouseX, mouseY);
        if (uv == null) return StickerToolState.GizmoAxis.NONE;
        var w = entry.window();
        double cx = w.offsetU(), cy = w.offsetV();
        double hw = w.width() * 0.5, hh = w.height() * 0.5;
        double arrowLen = 0.35, hitR = 0.16;
        if (near(uv, cx, cy + hh + arrowLen * 0.5, hitR)) return StickerToolState.GizmoAxis.UP;
        if (near(uv, cx, cy - hh - arrowLen * 0.5, hitR)) return StickerToolState.GizmoAxis.DOWN;
        if (near(uv, cx - hw - arrowLen * 0.5, cy, hitR)) return StickerToolState.GizmoAxis.LEFT;
        if (near(uv, cx + hw + arrowLen * 0.5, cy, hitR)) return StickerToolState.GizmoAxis.RIGHT;
        return StickerToolState.GizmoAxis.NONE;
    }

    private static boolean near(StickerPlaneMath.UV p, double tx, double ty, double r) {
        double dx = p.u() - tx, dy = p.v() - ty;
        return dx * dx + dy * dy <= r * r;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // Transparent world background — draw ONLY our draggable floating parameter panel + status bar
        gui.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xCC161A22);
        gui.fill(panelX, panelY, panelX + PANEL_W, panelY + TITLE_H, 0xEE263042);
        gui.renderOutline(panelX, panelY, PANEL_W, PANEL_H, 0xFF6B7B94);
        gui.drawString(font, "車窗參數 (拖動標題欄移動)", panelX + 8, panelY + 6, 0xFFFFFF);

        int y = panelY + TITLE_H + 10;
        String[] labels = {"邊框顏色", "玻璃顏色", "透明度", "邊框粗細", "圓角程度", "寬度", "高度", "水平偏移", "垂直偏移"};
        for (String lbl : labels) {
            gui.drawString(font, lbl, panelX + 10, y, 0xDCE3EC);
            y += 21;
        }

        String hint = StickerToolState.isCursorUnlocked()
                ? "WASD/空白/Shift 移動鏡頭 | 拖曳窗戶箭頭移動位置 | 右鍵鎖定視角 | ESC 儲存退出"
                : "視角鎖定中 — 移動滑鼠轉動鏡頭 | 右鍵解放滑鼠指標 | ESC 儲存退出";
        gui.drawCenteredString(font, hint, width / 2, height - 18, 0xFFE6A3);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        applyFieldsLive();
        long win = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(win, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        StickerEntry saved = StickerToolState.stopFreeCam();
        if (saved != null) HexieBridges.sync().sendStickerUpsert(saved);
        super.onClose();
    }

    private static boolean keyDown(long win, int key) {
        return GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
    }

    private static String fmt(float v) { return String.format(Locale.ROOT, "%.3f", v); }

    private static int parseColor(String s, int fallback) {
        try {
            String t = s.trim();
            if (t.startsWith("#")) t = t.substring(1);
            return Integer.parseInt(t, 16) & 0x00FFFFFF;
        } catch (Exception e) { return fallback; }
    }

    private static float parseFloat(String s, float fallback) {
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return fallback; }
    }
}
