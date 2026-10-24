package willow.train.hexie.client.screens.widget;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;

import willow.train.hexie.client.render.blockentity.LacqueredBoardEntityRenderer;
import willow.train.hexie.util.station.Connections;
import willow.train.hexie.util.station.LineMap;
import willow.train.hexie.util.station.LineTag;
import willow.train.hexie.util.station.StationRenderCache;

import java.util.ArrayList;
import java.util.Iterator;

public class LineMapScreenWidget extends AbstractWidget {
    boolean click = false;
    int lastX = 0, lastY = 0;
    LineMap map;
    ArrayList<StationButton> stationButtons = new ArrayList<>();
    ArrayList<ConnectionLine> connectionLines = new ArrayList<>();
    int visionCentralX, visionCentralY, dx, dy;
    float zoom = 1.0f;
    public LineTag chosenLine = null;

    public LineMapScreenWidget(LineMap map, int pX, int pY, int pWidth, int pHeight) {
        super(pX, pY, pWidth, pHeight, Component.empty());
        this.map = map;
        for (StationRenderCache cache : map.stationsLazy()) {
            stationButtons.add(new StationButton(cache, b -> {}));
        }

        for (Connections cons : map.connections()) {
            connectionLines.add(
                    new ConnectionLine(cons, map.getLine(cons.lineID).themeColor)
                            .line(cons.lineID));
        }

        this.visionCentralX = this.getX() + this.width / 2;
        this.visionCentralY = this.getY() + this.height / 2;

        dx = 0;
        dy = 0;

        translates(getX(), getY());
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {

        if (pMouseX < this.getX() || pMouseX > this.getX() + this.width) click = false;
        if (pMouseY < this.getY() || pMouseY > this.getY() + this.height) click = false;
        if (click) {
            if (pMouseX != lastX || lastY != pMouseY) {
                dx += (int) ((pMouseX - lastX) * zoom);
                dy += ((int) (pMouseY - lastY) * zoom);
                translates(pMouseX - lastX, pMouseY - lastY);
                lastX = pMouseX;
                lastY = pMouseY;
            }
        }

        BufferBuilder lineBuffer = null;
        for (ConnectionLine line : connectionLines) {
            if (!shouldRender(line)
                    || (chosenLine != null && line.lineID != chosenLine.lineID)
                    || !line.hasGeometry())
                continue;
            if (lineBuffer == null) {
                lineBuffer = Tesselator.getInstance().begin(
                        VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            }
            line.appendVertices(lineBuffer);
        }
        if (lineBuffer != null) {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, LacqueredBoardEntityRenderer.STICKER);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableBlend();
            BufferUploader.drawWithShader(lineBuffer.buildOrThrow());
            RenderSystem.disableBlend();
        }
        for (StationButton button : stationButtons) {
            if (chosenLine != null) {
                if (shouldRender(button) && button.station().station().lines().contains(chosenLine))
                    button.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
            } else {
                if (shouldRender(button))
                    button.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
            }
        }
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {}

    public boolean shouldRender(StationButton button) {
        if (button.getX() < this.getX()) return false;
        if (button.getX() + button.getWidth() > this.getX() + this.width) return false;
        if (button.getY() < this.getY()) return false;
        if (button.getY() + button.getHeight() > this.getX() + this.height) return false;
        return true;
    }

    public boolean shouldRender(ConnectionLine line) {
        if (line.pX1 < this.getX() || line.pX1 > this.getX() + this.width) return false;
        if (line.pY1 < this.getY() || line.pY1 > this.getY() + this.height) return false;
        if (line.pX2 > this.getX() + this.width || line.pX2 < this.getX()) return false;
        if (line.pY2 > this.getY() + this.height || line.pY2 < this.getY()) return false;
        return true;
    }

    public void translates(int px, int py) {
        for (ConnectionLine line : connectionLines) {
            line.pX1 += px;
            line.pY1 += py;
            line.pX2 += px;
            line.pY2 += py;
        }

        for (StationButton button : stationButtons) {
            button.setPos(button.staticX + px, button.staticY + py, button.scale);
        }
    }

    public void reset() {
        this.dx = 0;
        this.dy = 0;
        this.zoom = 1.0f;
        setScale(1.0f);
    }

    public void setScale(float zoom) {
        int i = 0;
        for (i = 0; i < map.connections().size(); i++) {
            ConnectionLine line = this.connectionLines.get(i);
            Connections con = map.connections().get(i);
            line.pX1 = (int) (con.x1 * zoom);
            line.pY1 = (int) (con.y1 * zoom);
            line.pX2 = (int) (con.x2 * zoom);
            line.pY2 = (int) (con.y2 * zoom);
        }
        i = 0;
        Iterator<StationRenderCache> stations = map.stationsLazy().iterator();
        for (i = 0; i < map.stationsLazy().size(); i++) {
            StationButton button = stationButtons.get(i);
            StationRenderCache cache = stations.next();
            button.setPos((int) (cache.x() * zoom), (int) (cache.y() * zoom), cache.scale() * zoom);
        }
        translates(getX() + (int) (dx * zoom), getY() + (int) (dy * zoom));

        translates(-(int) ((zoom - 1) * visionCentralX), -(int) ((zoom - 1) * visionCentralY));
    }

    @Override
    public void onClick(double pMouseX, double pMouseY) {
        super.onClick(pMouseX, pMouseY);
        if (!checkClicked(pMouseX, pMouseY)) {
            click = true;
            lastX = (int) pMouseX;
            lastY = (int) pMouseY;
        }
    }

    @Override
    public void onRelease(double pMouseX, double pMouseY) {
        super.onRelease(pMouseX, pMouseY);
        click = false;
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDeltaX, double pDelta) {
        if (this.zoom >= 0.2f && this.zoom <= 5.0f) {
            zoom += 0.05f * pDelta;
        } else if (this.zoom < 0.2f) {
            this.zoom = 0.2f;
        } else {
            this.zoom = 5.0f;
        }
        setScale(zoom);
        return true;
    }

    public boolean checkClicked(double pMouseX, double pMouseY) {
        for (StationButton button : stationButtons) {
            if (shouldRender(button)) {
                if (pMouseX > button.getX() && pMouseX < button.getX() + button.getWidth()) {
                    if (pMouseY > button.getY() && pMouseY < button.getY() + button.getHeight()) {
                        button.onClick(pMouseX, pMouseY);
                        button.onRelease(pMouseX, pMouseY);
                        return true;
                    }
                }
            }
        }

        for (ConnectionLine button : connectionLines) {
            if (shouldRender(button)) {
                if (pMouseX > Math.min(button.pX1, button.pX2)
                        && pMouseX < Math.max(button.pX1, button.pX2)) {
                    if (pMouseY > Math.min(button.pY1, button.pY2)
                            && pMouseY < Math.max(button.pY2, button.pY1)) {
                        button.onClick(pMouseX, pMouseY);
                        button.onRelease(pMouseX, pMouseY);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
