package willow.train.hexie.client.screens.widget;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import willow.train.hexie.Main;
import willow.train.hexie.util.station.LineTag;

import java.awt.*;

public class LineChosenButton extends Button {

    public boolean pressed = false;
    public int themeColor = 0xffffff;

    public LineTag tag;

    Font font;

    public static final ResourceLocation UP =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/tvm/line_button_up.png");
    public static final ResourceLocation DOWN =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/tvm/line_button_down.png");

    public LineChosenButton(
            LineTag line, int pX, int pY, int pWidth, int pHeight, OnPress pOnPress) {
        super(pX, pY, pWidth, pHeight, Component.empty(), pOnPress);
        this.font = Minecraft.getInstance().font;
        this.setMessage(Component.literal(line.lineName));
        this.themeColor = line.themeColor;
        this.tag = line;
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
        drawTexture(getX(), getY(), width, height, 1.0f, 1.0f, themeColor);
        int color = themeColor - 8388607;
        drawTextLazy(
                pGuiGraphics,
                font,
                this.getMessage().getString(),
                this.getX(),
                this.getY() + this.height / 2,
                0.7f,
                color < 0 ? color : -color);
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

    private void drawTextLazy(
            GuiGraphics poseStack, Font font, String pText, int x, int y, float scale, int color) {
        int original_width = font.width(pText);
        int original_height = font.lineHeight;
        poseStack.pose().pushPose();
        poseStack.pose().translate(
                x + ((float) this.width - ((float) original_width) * scale) / 2,
                y - ((float) original_height) * scale / 2,
                0);
        poseStack.pose().scale(scale, scale, 1.0f);
        willow.train.hexie.client.util.gui.GuiText.draw(poseStack, font, pText, 0, 0, color);
        poseStack.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        this.pressed = true;
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        this.pressed = false;
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }
}
