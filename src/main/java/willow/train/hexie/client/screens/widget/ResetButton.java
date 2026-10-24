package willow.train.hexie.client.screens.widget;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import willow.train.hexie.client.util.gui.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import willow.train.hexie.Main;

import java.awt.*;

public class ResetButton extends Button {

    public boolean pressed = false;

    public static final ResourceLocation UP =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/tvm/reset_button_up.png");
    public static final ResourceLocation DOWN =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/tvm/reset_button_down.png");

    public ResetButton(int pX, int pY, int pWidth, int pHeight, OnPress pOnPress) {
        super(pX, pY, pWidth, pHeight, Component.empty(), pOnPress);
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        // super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        if (this.pressed) {
            RenderSystem.setShaderTexture(0, DOWN);
        } else {
            RenderSystem.setShaderTexture(0, UP);
        }
        RenderSystem.enableBlend();
        drawTexture(getX(), getY(), width, height, 1.0f, 1.0f, 0xffffff);
    }

    private void drawTexture(
            int x, int y, int width, int height, float uWidth, float vHeight, int color) {
        int[] colors = getRGBAColor(color);
        int r = colors[0], g = colors[1], b = colors[2], a = colors[3];
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
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if(pMouseX > this.getX() && pMouseX < this.getX() + this.width && pMouseY > this.getY() && pMouseY < this.getY() + this.height) {
            this.pressed = true;
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        this.pressed = false;
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }
}
