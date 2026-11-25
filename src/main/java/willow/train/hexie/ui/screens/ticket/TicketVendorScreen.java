package willow.train.hexie.ui.screens.ticket;

// split from willow/train/hexie/client/screens/TicketVendorScreen.java

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.awt.*;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.util.station.LineMap;
import willow.train.hexie.core.util.station.LineTag;
import willow.train.hexie.ui.menu.TicketVendorMenu;
import willow.train.hexie.ui.screens.widget.LineChosenButton;
import willow.train.hexie.ui.screens.widget.LineMapScreenWidget;
import willow.train.hexie.ui.screens.widget.ResetButton;

public class TicketVendorScreen extends AbstractContainerScreen<TicketVendorMenu> {

    public static final ResourceLocation BG_MAIN =
            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "textures/gui/tvm/main.png");
    public static final ResourceLocation BG_MASK_DOWN =
            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "textures/gui/tvm/blank.png");
    public static final ResourceLocation BG_MASK_UP =
            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "textures/gui/tvm/upper_mask.png");
    public static final ResourceLocation BG_FRAME =
            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "textures/gui/tvm/edge.png");
    public static final ResourceLocation TVM_BUTTON_UP =
            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "textures/gui/tvm/button_up.png");

    private final TicketVendorRoutes routes = new TicketVendorRoutes();
    LineMapScreenWidget wiget;
    int guiLeft, guiTop, guiWidth;
    float porportion = 0.5f;

    ResetButton resetButton;

    ArrayList<LineChosenButton> lineButtons = new ArrayList<>();

    public TicketVendorScreen(
            TicketVendorMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
    }

    public void init() {

        guiLeft = (int) (0.1 * Minecraft.getInstance().screen.width);
        guiTop = (int) (0.1 * Minecraft.getInstance().screen.height);
        guiWidth = (int) (0.8 * Minecraft.getInstance().screen.width);

        routes.map = new LineMap("name");

        line1();
        line2();

        lineButtons.clear();

        wiget =
                new LineMapScreenWidget(
                        routes.map,
                        guiLeft + (int) (0.0933 * guiWidth) - 10,
                        guiTop + (int) (0.2067 * guiWidth * porportion) - 10,
                        (int) (0.4233 * guiWidth) + 20,
                        (int) (0.6733 * guiWidth * porportion) + 20);

        int counter = 0;
        for (LineTag tags : routes.map.lines()) {
            lineButtons.add(
                    new LineChosenButton(
                            tags,
                            (int) (0.1067 * guiWidth) + guiLeft,
                            (int) (0.2667 * guiWidth * porportion)
                                    + guiTop
                                    + counter * (int) (0.105 * guiWidth * 1 / 3),
                            (int) (0.1 * guiWidth),
                            (int) (0.1 * guiWidth * 1 / 3),
                            b -> {
                                wiget.chosenLine = tags;
                            }));
            counter++;
        }

        resetButton =
                new ResetButton(
                        guiLeft + (int) (0.1067 * guiWidth),
                        guiTop + (int) (0.81 * guiWidth * porportion),
                        (int) (0.03 * guiWidth),
                        (int) (0.03 * guiWidth * 10 / 9),
                        b -> {
                            wiget.chosenLine = null;
                            wiget.reset();
                        });
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        RenderSystem.setShaderTexture(0, BG_MASK_DOWN);
        RenderSystem.enableBlend();
        drawTexture(guiLeft, guiTop, guiWidth, (int) (porportion * guiWidth), 1.0f, 1.0f, 0xffffff);
        if (wiget != null) {
            wiget.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
            for (LineChosenButton button : lineButtons) {
                button.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
            }
            resetButton.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        }
        RenderSystem.setShaderTexture(0, BG_MASK_UP);
        drawTexture(guiLeft, guiTop, guiWidth, (int) (porportion * guiWidth), 1.0f, 1.0f, 0xffffff);
        RenderSystem.setShaderTexture(0, BG_MAIN);
        drawTexture(
                guiLeft,
                guiTop,
                guiWidth,
                (int) (porportion * guiWidth * 16 / 15),
                1.0f,
                1.0f,
                0xffffff);
        RenderSystem.setShaderTexture(0, BG_FRAME);
        drawTexture(guiLeft, guiTop, guiWidth, (int) (porportion * guiWidth), 1.0f, 1.0f, 0xffffff);
        RenderSystem.setShaderTexture(0, TVM_BUTTON_UP);
        drawTexture(guiLeft, guiTop, guiWidth, (int) (porportion * guiWidth), 1.0f, 1.0f, 0xffffff);
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {}

    private void drawTexture(
            int x, int y, int width, int height, float uWidth, float vHeight, int color) {
        int[] colors = getRGBAColor(color);
        int r = colors[0], g = colors[1], b = colors[2];
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(
                (float) r / 255.0f, (float) g / 255.0f, (float) b / 255.0f, 1.0f);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(x, y, 0.0F).setUv(0, 0);
        buffer.addVertex(x, y + height, 0.0F).setUv(0, vHeight);
        buffer.addVertex(x + width, y + height, 0.0F).setUv(uWidth, vHeight);
        buffer.addVertex(x + width, y, 0.0F).setUv(uWidth, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    public int[] getRGBAColor(int octColor) {
        int[] result = new int[4];
        Color color = new Color(octColor);
        result[0] = color.getRed();
        result[1] = color.getGreen();
        result[2] = color.getBlue();
        result[3] = color.getAlpha();
        return result;
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDeltaX, double pDelta) {
        if (wiget != null && inMap(pMouseX, pMouseY)) wiget.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta);
        return true;
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (wiget != null) {
            if (!clickedResetButton(pMouseX, pMouseY, pButton)
                    && !clickedLineButton(pMouseX, pMouseY, pButton)
                    && inMap(pMouseX, pMouseY)) wiget.mouseClicked(pMouseX, pMouseY, pButton);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (wiget != null) {
            wiget.mouseReleased(pMouseX, pMouseY, pButton);
            for (LineChosenButton b : lineButtons) {
                b.mouseReleased(pMouseX, pMouseY, pButton);
            }
            resetButton.mouseReleased(pMouseX, pMouseY, pButton);
        }
        return true;
    }

    public boolean inMap(double pMouseX, double pMouseY) {
        return pMouseX > wiget.getX()
                && pMouseX < wiget.getX() + wiget.getWidth()
                && pMouseY > wiget.getX()
                && pMouseY < wiget.getY() + wiget.getHeight();
    }

    public boolean clickedLineButton(double pMouseX, double pMouseY, int pButton) {
        for (LineChosenButton b : lineButtons) {
            if (pMouseX > b.getX()
                    && pMouseX < b.getX() + b.getWidth()
                    && pMouseY > b.getY()
                    && pMouseY < b.getY() + b.getHeight()) {
                b.mouseClicked(pMouseX, pMouseY, pButton);
                return true;
            }
        }
        return false;
    }

    public boolean clickedResetButton(double pMouseX, double pMouseY, int pButton) {
        if (pMouseX > resetButton.getX()
                && pMouseX < resetButton.getX() + resetButton.getWidth()
                && pMouseY > resetButton.getY()
                && pMouseY < resetButton.getY() + resetButton.getHeight()) {
            resetButton.mouseClicked(pMouseX, pMouseY, pButton);
            return true;
        }
        return false;
    }


    public void line1() {
        routes.line1();
    }

    public void line2() {
        routes.line2();
    }
}
