package willow.train.hexie.client.screens;


import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.Minecraft;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import willow.train.hexie.Main;
import willow.train.hexie.menu.CarriageNoSignEditMenu;
import willow.train.hexie.blockentity.CarriageNoSignEntity;

import java.util.Locale;

public class CarriageNoSignEditScreen extends AbstractContainerScreen<CarriageNoSignEditMenu> {
    private static final Component[] TEXT =
            new Component[] {
                Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_no_sign_edit_menu.content"), // 内容
                Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_no_sign_edit_menu.color"), // 内容
                Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.confirm"), // 确认
                Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.cancel"), // 取消
                Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_no_sign_edit_menu.hinge") // 对称
            };

    private static int editBoxWidth = 200;
    private static int editBoxHeight = 20;
    private static int offsetX = 70;
    private static int offsetY = -20;

    private CarriageNoSignEntity entity;

    EditBox content, color;

    private Button[] buttons = new Button[3];

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/gui.png");

    /**
     * 在 Menu 中将方块实体传递进来
     *
     * @param pMenu -
     * @param pPlayerInventory -
     * @param pTitle -
     */
    public CarriageNoSignEditScreen(
            CarriageNoSignEditMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        loadEntity(pMenu.getCtse());
    }

    /** 加载静态 Container 的类，在游戏启动时自动加载 */
    public void init() {
        int windowWidth = this.getMinecraft().screen.width;
        int centreX = windowWidth / 2;

        int xPos = centreX - editBoxWidth / 2 - offsetX;

        Minecraft minecraft = Minecraft.getInstance();
        content =
                new EditBox(
                        minecraft.font, xPos, 45 - offsetY, editBoxWidth, editBoxHeight, TEXT[0]);
        color =
                new EditBox(
                        minecraft.font, xPos, 80 - offsetY, editBoxWidth, editBoxHeight, TEXT[1]);

        content.setValue(entity.getMessage().getString());
        color.setValue(entity.getColor() + "");

        addRenderableWidget(content);
        addRenderableWidget(color);

        /*
           这个位置是定义按钮逻辑的
        */
        for (int i = 0; i < 3; i++) {
            buttons[i] =
                    addRenderableWidget(
                            new Button(
                                    xPos + i * editBoxWidth / 3 + 5,
                                    200,
                                    editBoxWidth / 3 - 10,
                                    editBoxHeight,
                                    TEXT[i + 2],
                                    b -> {
                                        if (b.equals(buttons[0])) {
                                            if (entity != null) {
                                                int c = 0;
                                                try {
                                                    String str =
                                                            this.color
                                                                    .getValue()
                                                                    .toLowerCase(Locale.ROOT)
                                                                    .replaceAll(" ", "");
                                                    if (str.equals(""))
                                                        str = entity.getColor() + "";
                                                    if (str.startsWith("0x")) {
                                                        c = Integer.parseInt(str.substring(2), 16);
                                                    } else {
                                                        c = Integer.parseInt(str);
                                                    }
                                                    entity.setColor(c);
                                                    entity.setMessage(
                                                            Component.literal(content.getValue()));
                                                                    // .setStyle(HEXIE_STANDARD_STYLE));
                                                    entity.markUpdated(true);
                                                    onClose();
                                                } catch (Exception e) {
                                                }
                                            }
                                        } else if (b.equals(buttons[1])) {
                                            onClose();
                                        } else if (b.equals(buttons[2])) {
                                            if (entity != null) {
                                                entity.setLeftSide(!entity.isLeftSide());
                                                entity.markUpdated(true);
                                            }
                                        }
                                    }));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        int windowWidth = this.getMinecraft().screen.width;

        int centreX = windowWidth / 2;

        willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                TEXT[0],
                centreX - this.font.width(TEXT[0].getString()) / 2 - offsetX,
                35 - offsetY,
                0xFFFFFF);
        willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                TEXT[1],
                centreX - this.font.width(TEXT[1].getString()) / 2 - offsetX,
                70 - offsetY,
                0xFFFFFF);
    }

    /**
     * 装载对应的方块实体
     *
     * @param entity 装载实体
     */
    public void loadEntity(CarriageNoSignEntity entity) {
        this.entity = entity;
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

    @Override
    public boolean keyReleased(int pKeyCode, int pScanCode, int pModifiers) {
        if (pKeyCode == 69) return false;
        return super.keyReleased(pKeyCode, pScanCode, pModifiers);
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        if (pKeyCode == 69) return false;
        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }
}
