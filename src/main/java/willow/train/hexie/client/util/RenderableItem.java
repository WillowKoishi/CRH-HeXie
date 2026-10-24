package willow.train.hexie.client.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class RenderableItem {
    private final ItemStack stack;
    private Font font;
    private boolean renderCount = false;
    private int width = 16, height = 16;
    public RenderableItem(ItemStack stack) {
        this.stack = stack;
        font = Minecraft.getInstance().font;
    }

    public RenderableItem(ItemStack stack, int width, int height) {
        this.stack = stack;
        font = Minecraft.getInstance().font;
        this.width = width;
        this.height = height;
    }

    public boolean isRenderingCount() {
        return renderCount;
    }

    public void renderCount(boolean TorF) {
        this.renderCount = TorF;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public ItemStack getStack() {
        return stack;
    }

    public void render(@Nullable GuiGraphics pose, ItemRenderer renderer, int x, int y, int width, int height){
        if(stack == null) return;
        renderItem(renderer, stack, x, y, width, height);
        if(renderCount && stack.isStackable() && stack.getCount() > 1 && pose != null) {
            String count = String.valueOf(stack.getCount());
            int w = font.width(count);
            willow.train.hexie.client.util.gui.GuiText.draw(pose, font, count, x + width - w, y + height - font.lineHeight, 0xffffff);
        }
    }

    public void render(@Nullable GuiGraphics pose, ItemRenderer renderer, int x, int y) {
        render(pose, renderer, x, y, width, height);
    }

    public void renderCentered(@Nullable GuiGraphics pose, ItemRenderer renderer, int centerX, int centerY, int width, int height) {
        render(pose, renderer, centerX - width/2, centerY - height/2);
    }

    public void renderCentered(@Nullable GuiGraphics pose, ItemRenderer renderer, int centerX, int centerY) {
        renderCentered(pose, renderer, centerX, centerY, width, height);
    }

    private void renderItem(ItemRenderer renderer, ItemStack item, int x, int y, int width, int height) {
        GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource());
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(width / 16.0F, height / 16.0F, 1.0F);
        graphics.renderItem(item, 0, 0);
        graphics.flush();
        graphics.pose().popPose();
    }
}
