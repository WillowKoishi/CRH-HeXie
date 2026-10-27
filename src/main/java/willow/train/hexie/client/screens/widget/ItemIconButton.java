package willow.train.hexie.client.screens.widget;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import willow.train.hexie.Main;

public class ItemIconButton extends Button {

    boolean toggled = false;
    private boolean expandWhenSelected = true;

    int x_offset = 0;

    int y_offset = 0;

    Component message = Component.empty();

    public static final ResourceLocation BUTTON_ICON =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/button_background.png");
    public static final ResourceLocation CHOSEN_BUTTON_ICON =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/chosen_button_background.png");

    ItemStack icon;

    public ItemIconButton(
            int pX,
            int pY,
            Component message,
            ItemStack icon,
            OnPress pOnPress,
            int x_offset,
            int y_offset) {
        super(pX, pY, 20, 20, Component.empty(), pOnPress);
        this.icon = icon;
        this.x_offset = x_offset;
        this.y_offset = y_offset;
        this.message = message;
    }

    public void setExpandWhenSelected(boolean expand) {
        if (toggled && expandWhenSelected != expand) setX(getX() + (expand ? -20 : 20));
        expandWhenSelected = expand;
    }

    public void toggle() {
        if (!toggled && expandWhenSelected) setX(getX() - 20);
        this.toggled = true;
    }

    public void reset() {
        if (toggled && expandWhenSelected) setX(getX() + 20);
        this.toggled = false;
    }

    public void renderWidget(GuiGraphics pGuiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        if (toggled) {
            RenderSystem.setShaderTexture(0, CHOSEN_BUTTON_ICON);
        } else {
            RenderSystem.setShaderTexture(0, BUTTON_ICON);
        }

        if (toggled) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
        } else {
            RenderSystem.setShaderColor(0.7F, 0.7F, 0.7F, this.alpha);
            ;
        }

        Font font = Minecraft.getInstance().font;

        int x = this.getX();
        int y = this.getY();

        if (toggled && expandWhenSelected) {
            this.drawTexture(x, y, 0, 0, 40, 20);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, font, message, x + 20, y + y_offset + 3, 0x4699FF);
        } else {
            this.drawTexture(x, y, 0, 0, 20, 20);
        }

        ItemRenderer renderer = mc.getItemRenderer();
        pGuiGraphics.renderItem(this.icon, x + x_offset, y + y_offset);
        pGuiGraphics.renderItemDecorations(mc.font, this.icon, x + x_offset, y + y_offset);
    }

    private void drawTexture(int x, int y, int textureX, int textureY, int width, int height) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(x, y, 0.0F).setUv(0, 0);
        buffer.addVertex(x, y + height, 0.0F).setUv(0, 1);
        buffer.addVertex(x + width, y + height, 0.0F).setUv(1, 1);
        buffer.addVertex(x + width, y, 0.0F).setUv(1, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

}
