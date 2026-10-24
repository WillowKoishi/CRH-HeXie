package willow.train.hexie.client.screens.controls.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import willow.train.hexie.client.screens.widget.DoubleImageButton;
import willow.train.hexie.client.util.GuiImageCache;
import willow.train.hexie.item.AutomaticControlCouplingItem;
import willow.train.hexie.client.util.RenderablePicture;


/**
 * Name -> Automatic Control Coupling Tag Box
 */
public class ACCTagBox extends AbstractWidget {
    public static GuiImageCache.ImageData CONTROLLER_IMAGE = GuiImageCache.register("textures/gui/controller/controller.png", 0);
    public final RenderablePicture background, buttonBgUp, buttonBgDown;

    public AutomaticControlCouplingItem.CouplingDisplayData data;

    public String index;
    public DoubleImageButton removalButton;
    private boolean shouldRemove = false;
    private float zoom;
    EditBox editBox;
    public ACCTagBox(AutomaticControlCouplingItem.CouplingDisplayData data, float zoom, int index) {
        super(0, 0, 110, 14, Component.empty());
        background = new RenderablePicture(CONTROLLER_IMAGE, 17, 81, 110, 14);
        background.zoom(zoom);
        buttonBgUp = new RenderablePicture(CONTROLLER_IMAGE, 0, 115, 12, 12);
        buttonBgDown = new RenderablePicture(CONTROLLER_IMAGE, 0, 103, 12, 12);
        this.data = data;
        this.index = String.valueOf(index);
        removalButton = new DoubleImageButton(buttonBgUp, buttonBgDown, 0, 0, (int)(12 * zoom), (int)(12 * zoom), b -> {this.shouldRemove = true;});
        removalButton.setZoom(zoom);
        removalButton.setX(getX() + (int)(95 * zoom));
        removalButton.setY(getY() + (int) zoom);
        editBox = new EditBox(Minecraft.getInstance().font, 0, 0, (int)(62 * zoom), (int)(8 * zoom), Component.literal(data.name));
        editBox.setValue(data.name);
        editBox.setBordered(false);
        editBox.setMaxLength(64);
        this.zoom = zoom;
    }

    public void setPosition(int x, int y) {
        this.setX(x);
        this.setY(y);
        editBox.setX(x + (int)(25 * zoom));
        editBox.setY(y + (int)(8 * zoom) - 1 - Minecraft.getInstance().font.lineHeight/2);
        removalButton.setX(x + (int)(95 * zoom));
        removalButton.setY(y + (int) zoom);
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
        height = (int)(14 * zoom);
        width = (int)(110 * zoom);
        background.zoom(zoom);
        buttonBgUp.zoom(zoom);
        buttonBgDown.zoom(zoom);
        editBox.setWidth((int)(62 * zoom));
        editBox.setHeight((int)(8 * zoom));
        removalButton.setWidth((int)(12 * zoom));
        removalButton.setHeight((int)(12 * zoom));
        editBox.setValue(data.name);
        setPosition(this.getX(), this.getY());
    }

    public boolean shouldRemove() {
        return shouldRemove;
    }

    public String getName() {
        return editBox.getValue();
    }

    public boolean isDirty() {
        if(data.name.equals(editBox.getValue())) {
            data.name = editBox.getValue();
            return true;
        }
        return false;
    }

    public String getIndex() {
        return index;
    }

    public AutomaticControlCouplingItem.CouplingDisplayData getNewData() {
        data.name = editBox.getValue().replaceAll(" ", "").equals("") ? data.name : editBox.getValue();
        return data;
    }

    @Override
    public void renderWidget(GuiGraphics pose, int pMouseX, int pMouseY, float pPartialTick) {
        background.renderLazy(getX(), getY());
        renderTextCentered(pose, index, getX() + (int)(9 * zoom), getY() + (int) (7 * zoom));
        editBox.render(pose, pMouseX, pMouseY, pPartialTick);
        removalButton.render(pose, pMouseX, pMouseY, pPartialTick);
    }

    public static void renderText(GuiGraphics pose, String content, float x, float y) {
        renderText(pose, Component.literal(content), x, y);
    }

    public static void renderText(GuiGraphics pose, Component content, float x, float y) {
        //willow.train.hexie.client.util.gui.GuiText.drawShadow(pose, Minecraft.getInstance().font, content, x + .5, y + 1, 0x1c1c1c);
        willow.train.hexie.client.util.gui.GuiText.draw(pose, Minecraft.getInstance().font, content, x, y, 0xffffff);
    }

    public static void renderTextCentered(GuiGraphics pose, String content, float centerX, float centerY) {
        renderTextCentered(pose, Component.literal(content), centerX, centerY);
    }

    public static void renderTextCentered(GuiGraphics pose, Component content, float centerX, float centerY) {
        Font font = Minecraft.getInstance().font;
        //willow.train.hexie.client.util.gui.GuiText.drawShadow(pose, font, content, centerX - (float) font.width(content)/2 + 1, centerY - (float) font.lineHeight/2 + 1, 0x1c1c1c);
        willow.train.hexie.client.util.gui.GuiText.draw(pose, font, content, centerX - (float) font.width(content)/2, centerY - (float) font.lineHeight/2, 0xffffff);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if(pMouseX < getX() || pMouseX > getX() + width*zoom || pMouseY < getY() || pMouseY > getY() + height*zoom) {
            if(editBox.isFocused())
                editBox.setFocused(false);
            return false;
        }
        if(removalButton.mouseClicked(pMouseX, pMouseY, pButton)) {
            return true;
        } else if(editBox.mouseClicked(pMouseX, pMouseY, pButton)) {
            if(!editBox.isFocused())
                editBox.setFocused(true);
            return true;
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        removalButton.mouseReleased(pMouseX, pMouseY, pButton);
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    public void forceLoseFocus() {
        if(editBox.isFocused()) editBox.setFocused(false);
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        if(editBox.isFocused()) {
            return editBox.keyPressed(pKeyCode, pScanCode, pModifiers);
        }
        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    @Override
    public boolean charTyped(char pCodePoint, int pModifiers) {
        return editBox.isFocused() && editBox.charTyped(pCodePoint, pModifiers);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {

    }
}
