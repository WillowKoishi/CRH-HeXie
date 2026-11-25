package willow.train.hexie.ui.sticker;

import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import willow.train.hexie.core.sticker.StickerMode;
import willow.train.hexie.core.sticker.WindowStickerParams;

/**
 * Mode & parameter selector opened by right-clicking air with the unified custom-sticker tool.
 * Lets the player switch between WINDOW (parametric geometry) and DECAL (local image upload).
 */
public final class StickerModeScreen extends Screen {
    private static final int PANEL_W = 290;
    private static final int PANEL_H = 236;

    private int left, top;
    private EditBox frameColorBox, glassColorBox, glassAlphaBox, thicknessBox, radiusBox, widthBox, heightBox;
    private EditBox imagePathBox;
    private String statusMessage = "";
    private String lastLoadedPath = "";

    public StickerModeScreen() {
        super(Component.literal("隨心貼模式與參數"));
    }

    @Override
    protected void init() {
        clearWidgets();
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;

        // Top mode toggle buttons
        addRenderableWidget(Button.builder(Component.literal(
                StickerToolState.mode() == StickerMode.WINDOW ? "● 幾何車窗" : "幾何車窗"), b -> {
            StickerToolState.setMode(StickerMode.WINDOW);
            init();
        }).bounds(left + 12, top + 24, 128, 20).build());

        addRenderableWidget(Button.builder(Component.literal(
                StickerToolState.mode() == StickerMode.DECAL ? "● 圖片貼花" : "圖片貼花"), b -> {
            StickerToolState.setMode(StickerMode.DECAL);
            init();
        }).bounds(left + 150, top + 24, 128, 20).build());

        if (StickerToolState.mode() == StickerMode.WINDOW) {
            initWindowControls();
        } else {
            initDecalControls();
        }

        addRenderableWidget(Button.builder(Component.literal("確定"), b -> {
            applyCurrentInputs();
            onClose();
        }).bounds(left + PANEL_W - 82, top + PANEL_H - 28, 70, 20).build());
    }

    private void initWindowControls() {
        WindowStickerParams p = StickerToolState.windowParams();
        int y = top + 54;
        frameColorBox = addRow("邊框顏色", String.format("#%06X", p.frameColorRgb()), y); y += 22;
        glassColorBox = addRow("玻璃顏色", String.format("#%06X", p.glassColorRgb()), y); y += 22;
        glassAlphaBox = addNumericRow("玻璃透明度", fmt(p.glassAlpha()), y, -0.05f, 0.05f, 0); y += 22;
        thicknessBox = addNumericRow("邊框粗細", fmt(p.frameThickness()), y, -0.015625f, 0.015625f, 1); y += 22;
        radiusBox = addNumericRow("圓角程度", fmt(p.cornerRadius()), y, -0.03125f, 0.03125f, 2); y += 22;
        widthBox = addNumericRow("寬度 (格)", fmt(p.width()), y, -0.0625f, 0.0625f, 3); y += 22;
        heightBox = addNumericRow("高度 (格)", fmt(p.height()), y, -0.0625f, 0.0625f, 4);
    }

    private EditBox addRow(String label, String initial, int y) {
        EditBox box = new EditBox(font, left + 112, y, 118, 18, Component.literal(label));
        box.setValue(initial);
        addRenderableWidget(box);
        return box;
    }

    private EditBox addNumericRow(String label, String initial, int y, float minusStep, float plusStep, int fieldIndex) {
        EditBox box = new EditBox(font, left + 112, y, 80, 18, Component.literal(label));
        box.setValue(initial);
        addRenderableWidget(box);
        addRenderableWidget(Button.builder(Component.literal("-"), b -> stepWindowField(fieldIndex, minusStep))
                .bounds(left + 196, y, 22, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> stepWindowField(fieldIndex, plusStep))
                .bounds(left + 220, y, 22, 18).build());
        return box;
    }

    private void stepWindowField(int fieldIndex, float delta) {
        applyWindowInputs();
        var p = StickerToolState.windowParams();
        var next = switch (fieldIndex) {
            case 0 -> new WindowStickerParams(p.frameColorRgb(), p.glassColorRgb(), p.glassAlpha() + delta,
                    p.frameThickness(), p.cornerRadius(), p.width(), p.height(), p.offsetU(), p.offsetV());
            case 1 -> new WindowStickerParams(p.frameColorRgb(), p.glassColorRgb(), p.glassAlpha(),
                    p.frameThickness() + delta, p.cornerRadius(), p.width(), p.height(), p.offsetU(), p.offsetV());
            case 2 -> new WindowStickerParams(p.frameColorRgb(), p.glassColorRgb(), p.glassAlpha(),
                    p.frameThickness(), p.cornerRadius() + delta, p.width(), p.height(), p.offsetU(), p.offsetV());
            case 3 -> p.withSize(p.width() + delta, p.height());
            case 4 -> p.withSize(p.width(), p.height() + delta);
            default -> p;
        };
        StickerToolState.setWindowParams(next);
        init();
    }

    private void initDecalControls() {
        imagePathBox = new EditBox(font, left + 14, top + 64, PANEL_W - 28, 18, Component.literal("圖片路徑"));
        imagePathBox.setMaxLength(512);
        addRenderableWidget(imagePathBox);

        addRenderableWidget(Button.builder(Component.literal("瀏覽本地圖片..."), b -> openNativeFileDialog())
                .bounds(left + 14, top + 90, 132, 20).build());

        addRenderableWidget(Button.builder(Component.literal("載入路徑"), b -> loadPath(imagePathBox.getValue()))
                .bounds(left + 154, top + 90, 122, 20).build());
    }

    private void openNativeFileDialog() {
        Thread picker = new Thread(() -> {
            String picked;
            try {
                picked = pickWithTinyFd();
            } catch (Throwable t) {
                picked = pickWithAwtFallback();
            }
            if (picked != null && !picked.isBlank()) {
                final String finalPath = picked;
                Minecraft.getInstance().execute(() -> {
                    if (imagePathBox != null) imagePathBox.setValue(finalPath);
                    loadPath(finalPath);
                });
            }
        }, "HeXie-Sticker-FilePicker");
        picker.setDaemon(true);
        picker.start();
    }

    private String pickWithTinyFd() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.png"));
            filters.put(stack.UTF8("*.jpg"));
            filters.put(stack.UTF8("*.jpeg"));
            filters.put(stack.UTF8("*.bmp"));
            filters.flip();
            return TinyFileDialogs.tinyfd_openFileDialog(
                    "Select Decal Image (PNG/JPG)", "", filters, "Image Files (*.png, *.jpg, *.jpeg, *.bmp)", false);
        }
    }

    private String pickWithAwtFallback() {
        try {
            FileDialog dialog = new FileDialog((Frame) null, "選擇隨心貼圖片 (PNG/JPG)", FileDialog.LOAD);
            dialog.setFile("*.png;*.jpg;*.jpeg;*.bmp");
            dialog.setVisible(true);
            String dir = dialog.getDirectory();
            String file = dialog.getFile();
            dialog.dispose();
            return (dir != null && file != null) ? Path.of(dir, file).toString() : null;
        } catch (Throwable t) {
            Minecraft.getInstance().execute(() -> statusMessage = "無法開啟檔案對話框，請直接貼上圖片路徑");
            return null;
        }
    }

    private void loadPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            statusMessage = "請先選擇或輸入圖片路徑";
            return;
        }
        String cleaned = rawPath.trim().replace("\"", "");
        Path p = Path.of(cleaned);
        if (!Files.isRegularFile(p)) {
            statusMessage = "找不到檔案: " + p.getFileName();
            return;
        }
        String err = StickerToolState.loadLocalImage(p);
        if (err == null) {
            lastLoadedPath = cleaned;
            statusMessage = "已載入本地預覽（右鍵方塊確認上傳）";
        } else {
            statusMessage = err;
        }
    }

    private void applyWindowInputs() {
        if (StickerToolState.mode() != StickerMode.WINDOW || frameColorBox == null) return;
        var cur = StickerToolState.windowParams();
        StickerToolState.setWindowParams(new WindowStickerParams(
                parseColor(frameColorBox.getValue(), cur.frameColorRgb()),
                parseColor(glassColorBox.getValue(), cur.glassColorRgb()),
                parseFloat(glassAlphaBox.getValue(), cur.glassAlpha()),
                parseFloat(thicknessBox.getValue(), cur.frameThickness()),
                parseFloat(radiusBox.getValue(), cur.cornerRadius()),
                parseFloat(widthBox.getValue(), cur.width()),
                parseFloat(heightBox.getValue(), cur.height()),
                cur.offsetU(),
                cur.offsetV()));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(left, top, left + PANEL_W, top + PANEL_H, 0xCC181C24);
        gui.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF5A6577);
        gui.drawCenteredString(font, title, left + PANEL_W / 2, top + 8, 0xFFFFFF);

        if (StickerToolState.mode() == StickerMode.WINDOW) {
            int y = top + 59;
            gui.drawString(font, "邊框顏色", left + 16, y, 0xDCE3EC); y += 22;
            gui.drawString(font, "玻璃顏色", left + 16, y, 0xDCE3EC); y += 22;
            gui.drawString(font, "玻璃透明度", left + 16, y, 0xDCE3EC); y += 22;
            gui.drawString(font, "邊框粗細", left + 16, y, 0xDCE3EC); y += 22;
            gui.drawString(font, "圓角程度", left + 16, y, 0xDCE3EC); y += 22;
            gui.drawString(font, "寬度 (格)", left + 16, y, 0xDCE3EC); y += 22;
            gui.drawString(font, "高度 (格)", left + 16, y, 0xDCE3EC);
            gui.drawString(font, "預覽: 按住 Ctrl 進入 16x16 精細放置", left + 14, top + PANEL_H - 22, 0x9BB4D0);
        } else {
            gui.drawString(font, "本地圖片路徑 (PNG/JPG):", left + 14, top + 52, 0xDCE3EC);
            String loaded = StickerToolState.hasLocalImage()
                    ? "已載入: " + StickerToolState.localImageName()
                    : "尚未選擇圖片";
            gui.drawString(font, loaded, left + 14, top + 122, 0xA8E0B4);
            gui.drawString(font, "操作: 滾輪縮放 | Ctrl+滾輪旋轉 | 右鍵確認上傳", left + 14, top + 140, 0x9BB4D0);
            if (!statusMessage.isEmpty()) {
                gui.drawString(font, statusMessage, left + 14, top + 160, 0xFFD27F);
            }
        }
        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }

    private void applyCurrentInputs() {
        if (StickerToolState.mode() == StickerMode.WINDOW) {
            applyWindowInputs();
        } else if (imagePathBox != null) {
            String raw = imagePathBox.getValue().trim().replace("\"", "");
            if (!raw.isEmpty() && (!StickerToolState.hasLocalImage() || !raw.equals(lastLoadedPath))) {
                loadPath(raw);
            }
        }
    }

    @Override
    public void onClose() {
        applyCurrentInputs();
        super.onClose();
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static int parseColor(String s, int fallback) {
        try {
            String t = s.trim();
            if (t.startsWith("#")) t = t.substring(1);
            return Integer.parseInt(t, 16) & 0x00FFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float parseFloat(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
