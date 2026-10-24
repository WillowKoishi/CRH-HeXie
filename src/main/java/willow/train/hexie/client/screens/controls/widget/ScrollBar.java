package willow.train.hexie.client.screens.controls.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import willow.train.hexie.client.screens.HexieInventory;
import willow.train.hexie.client.util.RenderablePicture;

public class ScrollBar extends AbstractWidget {
    private boolean vertical, working;
    private int exactLength, rencent, elementCount;
    private final RenderablePicture boundsVertical, boundsHorizontal, ball;
    public ScrollBar(int pX, int pY, int pWidth, int pHeight, int exactLength, boolean vertical) {
        super(pX, pY, pWidth, pHeight, Component.empty());
        this.vertical = vertical;
        this.exactLength = exactLength;
        boundsVertical = new RenderablePicture(HexieInventory.INVENTORY_IMAGE, 176, 32, 4, 3);
        boundsHorizontal = new RenderablePicture(HexieInventory.INVENTORY_IMAGE, 176, 35, 3, 4);
        ball = new RenderablePicture(HexieInventory.INVENTORY_IMAGE, 180, 32, 6, 6);
    }

    public void setExactLength(int exactLength) {
        this.exactLength = exactLength;
        setRencent(rencent);
    }

    public void setRencent(int rencent) {
        this.rencent = Math.max(0, Math.min(rencent, exactLength - elementCount));
    }

    public int getRencent() {
        return rencent;
    }

    public void setElementCount(int elementCount) {
        this.elementCount = elementCount;
    }

    private int getRecentY() {
        return getY() + 1 + 2/ball.height() + (int)(((float)rencent / (float) (exactLength - elementCount)) * (this.height - 2 - ball.height()));
    }

    private int getRecentX() {
        return getX() + 1 + 2/ball.width() + (int)(((float)rencent / (float) (exactLength - elementCount)) * (this.width - 2 - ball.width()));
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        if(elementCount >= exactLength) return;
        if(working) {
            double percentage = vertical ? (pMouseY - getY())/(double) height : (pMouseX - getX())/(double) width;
            setRencent((int)Math.round(exactLength * percentage));
        }
        if(vertical) {
            boundsVertical.renderLazy(getX() + 1, getY());
            boundsVertical.renderLazy(getX() + 1, getY() + height - boundsVertical.height());
            ball.renderLazy(getX(), getRecentY());
        } else {
            boundsHorizontal.renderLazy(getX(), getY() + 1);
            boundsHorizontal.renderLazy(getX() + width - boundsHorizontal.width(), getY() + 1);
            ball.renderLazy(getRecentX(), getY());
        }
    }

    @Override
    public void onClick(double pMouseX, double pMouseY) {
        if(elementCount >= exactLength) return;
        if(pMouseX < getX() || pMouseX > getX() + width || pMouseY < getY() || pMouseY > getY() + height) return;
        working = true;
        super.onClick(pMouseX, pMouseY);
    }

    @Override
    public void onRelease(double pMouseX, double pMouseY) {
        working = false;
        super.onRelease(pMouseX, pMouseY);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDeltaX, double pDelta) {
        setRencent(rencent + (int)Math.signum(-pDelta));
        return super.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {}
}
