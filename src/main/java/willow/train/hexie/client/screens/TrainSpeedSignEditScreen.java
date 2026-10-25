package willow.train.hexie.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractOptionSliderButton;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import willow.train.hexie.Main;
import willow.train.hexie.menu.TrainSpeedSignEditMenu;
import willow.train.hexie.blockentity.TrainSpeedSignEntity;

import java.awt.*;
import java.util.Locale;

public class TrainSpeedSignEditScreen extends AbstractContainerScreen<TrainSpeedSignEditMenu> {
    private static final Component[] TEXT =
            new Component[] {
                Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_no_sign_edit_menu.content"), // 内容 0
                Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_no_sign_edit_menu.color"), // 颜色 1
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".train_speed_sign_edit_menu.x_offset"), // x轴偏移 2
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".train_speed_sign_edit_menu.y_offset"), // y轴偏移 3
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".train_speed_sign_edit_menu.x_revert"), // y轴反转 4
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".train_speed_sign_edit_menu.y_revert"), // y轴反转 5
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.confirm"), // 确认 6
                Component.translatable(
                        "container." + Main.MOD_ID + ".train_speed_sign_edit_menu.reset"), // 归零 6
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.cancel"), // 取消 7
            };

    private static int editBoxWidth = 200;
    private static int editBoxHeight = 20;
    private static int offsetX = 70;
    private static int offsetY = 10;

    private TrainSpeedSignEntity entity;

    EditBox content, color;
    int turnOnColor = 0xA3FF92, turnOffColor = 0xFF9292;
    SildingBarButton x_offset, y_offset;

    private Button[] buttons = new Button[3];

    private Button[] revert_buttons = new Button[2];

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/gui.png");

    /**
     * 在 Menu 中将方块实体传递进来
     *
     * @param pMenu -
     * @param pPlayerInventory -
     * @param pTitle -
     */
    public TrainSpeedSignEditScreen(
            TrainSpeedSignEditMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        loadEntity(pMenu.getTsse());
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

        x_offset =
                new SildingBarButton(
                        this.minecraft,
                        xPos,
                        110 - offsetY,
                        editBoxWidth,
                        editBoxHeight,
                        entity.getX_offset() + 0.5,
                        TEXT[2]);
        y_offset =
                new SildingBarButton(
                        this.minecraft,
                        xPos,
                        135 - offsetY,
                        editBoxWidth,
                        editBoxHeight,
                        entity.getY_offset() + 0.5,
                        TEXT[3]);

        content.setValue(entity.getContent().getString());
        color.setValue(entity.getColor() + "");

        addRenderableWidget(content);
        addRenderableWidget(color);

        for (int i = 0; i < 2; i++) {
            revert_buttons[i] =
                    addRenderableWidget(
                            new Button(
                                    xPos + i * editBoxWidth / 2 + 5,
                                    175,
                                    editBoxWidth / 2 - 10,
                                    editBoxHeight,
                                    TEXT[i + 4],
                                    b -> {
                                        if (b.equals(revert_buttons[0])) {
                                            if (entity != null) {
                                                entity.revert_x();
                                                b.setFGColor(
                                                        entity.isX_revert()
                                                                ? turnOnColor
                                                                : turnOffColor);
                                            }
                                        } else if (b.equals(revert_buttons[1])) {
                                            if (entity != null) {
                                                entity.revert_y();
                                                b.setFGColor(
                                                        entity.isY_revert()
                                                                ? turnOnColor
                                                                : turnOffColor);
                                            }
                                        }
                                    }));
        }

        revert_buttons[0].setFGColor(entity.isX_revert() ? turnOnColor : turnOffColor);
        revert_buttons[1].setFGColor(entity.isY_revert() ? turnOnColor : turnOffColor);

        addRenderableWidget(x_offset);
        addRenderableWidget(y_offset);

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
                                    TEXT[i + 6],
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
                                                    entity.setContent(
                                                            Component.literal(content.getValue()));
                                                    entity.setX_offset(
                                                            (float) (x_offset.getValue() - 0.5));
                                                    entity.setY_offset(
                                                            (float) (y_offset.getValue() - 0.5));
                                                    entity.markUpdated(true);
                                                    onClose();
                                                } catch (Exception e) {
                                                }
                                            }
                                        } else if (b.equals(buttons[1])) {
                                            entity.setX_offset(0.0f);
                                            entity.setY_offset(0.0f);
                                            x_offset.setValue(0.5);
                                            y_offset.setValue(0.5);
                                            // onClose();
                                        } else if (b.equals(buttons[2])) {
                                            onClose();
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
    public void loadEntity(TrainSpeedSignEntity entity) {
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
        return false;
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

    class SildingBarButton extends AbstractOptionSliderButton {

        private final Component msg;

        private boolean onClicked;

        protected SildingBarButton(
                Minecraft pMinecraft,
                int pX,
                int pY,
                int pWidth,
                int pHeight,
                double pValue,
                Component msg) {
            super(pMinecraft.options, pX, pY, pWidth, pHeight, pValue);
            this.value = pValue;
            this.msg = msg;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(
                    msg.copy()
                            .append(Component.literal(" = " + String.format("%.3f", value - 0.5))));
        }

        @Override
        protected void applyValue() {}

        public void setValue(double value) {
            this.value = value;
            updateMessage();
        }

        @Override
        public void onClick(double pMouseX, double pMouseY) {
            super.onClick(pMouseX, pMouseY);
            this.onClicked = true;
        }

        @Override
        public void onRelease(double pMouseX, double pMouseY) {
            super.onRelease(pMouseX, pMouseY);
            this.onClicked = false;
        }

        @Override
        public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
            if(!onClicked) return false;
            double percentage = (pMouseX - this.getX())/(double) this.width;
            this.setValue(percentage);
            return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
        }

        public double getValue() {
            return value;
        }
    }
}
