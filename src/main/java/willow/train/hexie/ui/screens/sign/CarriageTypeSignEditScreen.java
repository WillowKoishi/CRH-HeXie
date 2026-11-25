package willow.train.hexie.ui.screens.sign;

// split from willow/train/hexie/client/screens/CarriageTypeSignEditScreen.java

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.ui.menu.CarriageTypeSignEditMenu;

public class CarriageTypeSignEditScreen extends CarriageTypeSignEditorControls {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HexieConstants.MOD_ID, "textures/gui/gui.png");

    /**
     * 在 Menu 中将方块实体传递进来
     *
     * @param pMenu -
     * @param pPlayerInventory -
     * @param pTitle -
     */
    public CarriageTypeSignEditScreen(
            CarriageTypeSignEditMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        int windowWidth = this.getMinecraft().screen.width;

        int centreX = windowWidth / 2;

        if (!editColor && !randomPanel) {
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[0],
                    centreX - this.font.width(TEXT[0].getString()) / 2 - offsetX,
                    35 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[1],
                    centreX - this.font.width(TEXT[1].getString()) / 2 - offsetX,
                    70 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[2],
                    centreX - this.font.width(TEXT[2].getString()) / 2 - offsetX,
                    105 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[3],
                    centreX - this.font.width(TEXT[3].getString()) / 2 - offsetX,
                    140 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[4],
                    centreX - this.font.width(TEXT[4].getString()) / 2 - offsetX,
                    175 - offsetY,
                    0xFFFFFF);
        } else if (editColor) {
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[5],
                    centreX - this.font.width(TEXT[5].getString()) / 2 - offsetX,
                    35 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[6],
                    centreX - this.font.width(TEXT[6].getString()) / 2 - offsetX,
                    80 - offsetY,
                    0xFFFFFF);
        } else if (randomPanel) {
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    random_title_l,
                    random_min.getX()
                            + editBoxWidth / 6
                            - this.font.width(random_title_l.getString()) / 2
                            - 5,
                    40 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    random_title_c,
                    random_box.getX()
                            + editBoxWidth / 6
                            - this.font.width(random_title_c.getString()) / 2
                            - 5,
                    40 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                    random_title_r,
                    random_max.getX()
                            + editBoxWidth / 6
                            - this.font.width(random_title_r.getString()) / 2
                            - 5,
                    40 - offsetY,
                    0xFFFFFF);
            for (int i = 0; i < random_tooltips.length; i++) {
                willow.train.hexie.ui.screens.widget.GuiText.draw(pGuiGraphics, this.font,
                        random_tooltips[i],
                        centreX - this.font.width(random_tooltips[i].getString()) / 2 - offsetX,
                        105 - offsetY + i * 14,
                        0xFFFFFF);
            }
        }
    }

    /**
     * 这个方法用于渲染背景
     *
     * @param pGuiGraphics -
     * @param pPartialTick -
     * @param pMouseX -
     * @param pMouseY -
     */
    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        pGuiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

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
