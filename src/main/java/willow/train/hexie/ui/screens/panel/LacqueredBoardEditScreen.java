package willow.train.hexie.ui.screens.panel;

// split from willow/train/hexie/client/screens/LacqueredBoardEditScreen.java

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import willow.train.hexie.ui.menu.LacqueredBoardEditMenu;

public class LacqueredBoardEditScreen extends LacqueredBoardEditorControls {

    /**
     * 在 Menu 中将方块实体传递进来
     *
     * @param pMenu -
     * @param pPlayerInventory -
     * @param pTitle -
     */
    public LacqueredBoardEditScreen(
            LacqueredBoardEditMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        int windowWidth = this.getMinecraft().screen.width;

        int centreX = windowWidth / 2;

        if (!editingColors) {
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[0],
                    centreX - this.font.width(TEXT[0].getString()) / 2 - offsetX,
                    33 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[1],
                    centreX - this.font.width(TEXT[1].getString()) / 2 - offsetX,
                    63 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[2],
                    centreX - this.font.width(TEXT[2].getString()) / 2 - offsetX,
                    93 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[3],
                    centreX - this.font.width(TEXT[3].getString()) / 2 - offsetX,
                    123 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[4],
                    centreX - this.font.width(TEXT[4].getString()) / 2 - offsetX,
                    153 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[5],
                    centreX - this.font.width(TEXT[5].getString()) / 2 - offsetX,
                    183 - offsetY,
                    0xFFFFFF);
        } else {
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[6],
                    centreX - this.font.width(TEXT[6].getString()) / 2 - offsetX,
                    33 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[7],
                    centreX - this.font.width(TEXT[7].getString()) / 2 - offsetX,
                    63 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[8],
                    centreX - this.font.width(TEXT[8].getString()) / 2 - offsetX,
                    93 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[9],
                    centreX - this.font.width(TEXT[9].getString()) / 2 - offsetX,
                    123 - offsetY,
                    0xFFFFFF);
        }
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {}

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        renderBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        renderTooltip(pGuiGraphics, pMouseX, pMouseY);
    }

    public boolean isPauseScreen() {
        return true;
    }

}
