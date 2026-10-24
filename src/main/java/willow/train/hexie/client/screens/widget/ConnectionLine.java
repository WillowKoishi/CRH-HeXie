package willow.train.hexie.client.screens.widget;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;

import willow.train.hexie.client.render.blockentity.LacqueredBoardEntityRenderer;
import willow.train.hexie.util.station.Connections;

import java.awt.*;

public class ConnectionLine extends AbstractWidget {

    int pX1, pY1, pX2, pY2, themeColor;
    int lineWidth = 2;
    int tactics = 0;

    long lineID = -1;

    public ConnectionLine(int pX1, int pY1, int pX2, int pY2, int themeColor) {
        super(
                Math.min(pX1, pX2),
                Math.min(pY1, pY2),
                Math.abs(pX2 - pX1),
                Math.abs(pY2 - pY1),
                Component.empty());
        this.pX1 = pX1;
        this.pY1 = pY1;
        this.pX2 = pX2;
        this.pY2 = pY2;
        this.themeColor = themeColor;
    }

    public ConnectionLine(Connections connections, int themeColor) {
        this(connections.x1, connections.y1, connections.x2, connections.y2, themeColor);
        this.lineWidth = connections.lineWidth;
        this.tactics = connections.tatics;
    }

    public ConnectionLine(Vec2 pos1, Vec2 pos2, int themeColor) {
        super(
                (int) Math.min(pos1.x, pos2.x),
                (int) Math.min(pos1.y, pos2.y),
                (int) Math.abs(pos2.x - pos1.x),
                (int) Math.abs(pos2.y - pos1.y),
                Component.empty());
        this.pX1 = (int) pos1.x;
        this.pY1 = (int) pos1.y;
        this.pX2 = (int) pos2.x;
        this.pY2 = (int) pos2.y;
        this.themeColor = themeColor;
    }

    public ConnectionLine line(long line) {
        this.lineID = line;
        return this;
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        if (!hasGeometry()) return;
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        appendVertices(buffer);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, LacqueredBoardEntityRenderer.STICKER);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    public boolean hasGeometry() {
        return pX1 != pX2 || pY1 != pY2;
    }

    public void appendVertices(BufferBuilder buffer) {
        if (!hasGeometry()) return;
        int dx = pX2 - pX1;
        int dy = pY2 - pY1;
        int half_line_width = lineWidth / 2;
        int r = (themeColor >> 16) & 0xff;
        int g = (themeColor >> 8) & 0xff;
        int b = themeColor & 0xff;
        float angle_factor = 0.2f;
        if (dx != 0 && dy != 0) {
            int stepXY = (int) (Math.min(Math.abs(dx), Math.abs(dy)) * angle_factor);
            int signDx = (int) Math.signum(dx), signDy = (int) Math.signum(dy);

            int angleX1, angleY1;
            int angleX2, angleY2;

            if (tactics == 1) {
                angleX1 = pX2 - signDx * stepXY;
                angleY1 = pY1;
                angleX2 = pX2;
                angleY2 = pY1 + signDy * stepXY;
            } else {
                angleX1 = pX1;
                angleY1 = pY2 - signDy * stepXY;
                angleX2 = pX1 + signDx * stepXY;
                angleY2 = pY2;
            }

            // 第一段 (x1, y1) -> (angleX1, angleY1)
            if (pX1 == angleX1) {
                appendTexture(buffer,
                        pX1 - half_line_width,
                        Math.min(pY1, angleY1),
                        lineWidth,
                        Math.abs(angleY1 - pY1),
                        1.0f,
                        1.0f, r, g, b);
            }
            if (pY1 == angleY1) {
                appendTexture(buffer,
                        Math.min(pX1, angleX1),
                        pY1 - half_line_width,
                        Math.abs(angleX1 - pX1),
                        lineWidth,
                        1.0f,
                        1.0f, r, g, b);
            }

            // 第二段 (angleX1, angleY1) -> (angleX2, angleY2)

            for (int i = 0; i < stepXY + 1; i++) {
                int x0 = angleX1 - half_line_width + i * signDx;
                int y0 = angleY1 - half_line_width + i * signDy;
                appendTexture(buffer, x0, y0, lineWidth, lineWidth, 1.0f, 1.0f, r, g, b);
            }

            // 第三段 (angleX2, angleY2) -> (x2, y2)
            if (pX2 == angleX2) {

                appendTexture(buffer,
                        pX2 - half_line_width,
                        Math.min(pY2, angleY2),
                        lineWidth,
                        Math.abs(pY2 - angleY2),
                        1.0f,
                        1.0f, r, g, b);
            }
            if (pY2 == angleY2) {
                appendTexture(buffer,
                        Math.min(pX2, angleX2),
                        pY2 - half_line_width,
                        Math.abs(pX2 - angleX2),
                        lineWidth,
                        1.0f,
                        1.0f, r, g, b);
            }
        } else if (pX1 == pX2) {
            appendTexture(buffer,
                    pX1 - half_line_width,
                    Math.min(pY1, pY2),
                    lineWidth,
                    Math.abs(pY2 - pY1),
                    1.0f,
                    1.0f, r, g, b);
        } else if (pY1 == pY2) {
            appendTexture(buffer,
                    Math.min(pX1, pX2),
                    pY1 - half_line_width,
                    Math.abs(pX1 - pX2),
                    lineWidth,
                    1.0f,
                    1.0f, r, g, b);
        }
    }

    private static void appendTexture(
            BufferBuilder buffer, int x, int y, int width, int height,
            float uWidth, float vHeight, int r, int g, int b) {
        buffer.addVertex(x, y, 0.0F).setUv(0, 0).setColor(r, g, b, 255);
        buffer.addVertex(x, y + height, 0.0F).setUv(0, vHeight).setColor(r, g, b, 255);
        buffer.addVertex(x + width, y + height, 0.0F).setUv(uWidth, vHeight).setColor(r, g, b, 255);
        buffer.addVertex(x + width, y, 0.0F).setUv(uWidth, 0).setColor(r, g, b, 255);
    }

    public int[] getRGBAColor() {
        int[] result = new int[4];
        Color color = new Color(themeColor);
        result[0] = color.getRed();
        result[1] = color.getGreen();
        result[2] = color.getBlue();
        result[3] = color.getAlpha();
        return result;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {}

    @Override
    public void onRelease(double pMouseX, double pMouseY) {
        if (this.tactics == 0) {
            tactics = 1;
        } else {
            tactics = 0;
        }
    }
}
