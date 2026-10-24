package willow.train.hexie.client.screens.widget;

import net.minecraft.client.gui.GuiGraphics;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import willow.train.hexie.client.util.RenderablePicture;

@OnlyIn(Dist.CLIENT)
public class DoubleImageButton extends Button {

    RenderablePicture upPicture, downPicture;
    public float zoom = 1.0f;
    public int org_width, org_height;
    public boolean isDown = false;

    public DoubleImageButton(RenderablePicture upPicture, RenderablePicture downPicture, int x, int y, int width, int height, OnPress press) {
        super(0, 0, upPicture.width(), upPicture.height(), Component.empty(), press);
        this.upPicture = upPicture;
        this.downPicture = downPicture;
        this.width = width;
        this.height = height;
        this.setX(x);
        this.setY(y);
        this.org_height = height;
        this.org_width = width;
    }

    public DoubleImageButton(RenderablePicture upPicture, RenderablePicture downPicture, OnPress press) {
        this(upPicture, downPicture, 0, 0, upPicture.width(), upPicture.height(), press);
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
        this.width = (int)(org_width * zoom);
        this.height = (int)(org_height * zoom);
        this.upPicture.zoom(zoom);
        this.downPicture.zoom(zoom);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if(clicked(pMouseX, pMouseY)) this.isDown = true;
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public void onClick(double pMouseX, double pMouseY) {}

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if(isDown) {
            super.onClick(pMouseX, pMouseY);
            this.isDown = false;
            return super.mouseReleased(pMouseX, pMouseY, pButton);
        }
        return false;
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        if(isDown)
            downPicture.renderLazy(getX(), getY());
        else
            upPicture.renderLazy(getX(), getY());
    }
}
