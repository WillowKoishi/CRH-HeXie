package willow.train.hexie.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import willow.train.hexie.menu.StandardTrackPavingMenu;

/** Lets a player choose a paving material from inventory without moving it. */
public final class StandardTrackPavingScreen extends AbstractContainerScreen<StandardTrackPavingMenu> {
    public StandardTrackPavingScreen(StandardTrackPavingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 184;
        inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF242424);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 20, 0xFF3A3A3A);
        graphics.fill(leftPos + 1, topPos + 67, leftPos + imageWidth - 1, topPos + 82, 0xFF343434);
        graphics.fill(leftPos + 1, topPos + 133, leftPos + imageWidth - 1, topPos + 183, 0xFF343434);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF777777);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.hexie.standard_track_paving.material"),
                8, 38, 0xE0E0E0, false);
        graphics.drawString(font, Component.translatable("gui.hexie.standard_track_paving.select_hint"),
                8, 54, 0xAAAAAA, false);
        graphics.drawString(font, Component.translatable("gui.hexie.standard_track_paving.reset_hint"),
                8, 64, 0xAAAAAA, false);
        graphics.drawString(font, Component.translatable("container.inventory"),
                inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
