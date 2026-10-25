package willow.train.hexie.client.screens;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.Minecraft;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import willow.train.hexie.Main;
import willow.train.hexie.menu.LacqueredBoardEditMenu;
import willow.train.hexie.blockentity.LacqueredBoardEntity;

import java.util.Locale;

public class LacqueredBoardEditScreen extends AbstractContainerScreen<LacqueredBoardEditMenu> {

    private static final Component[] TEXT =
            new Component[] {
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.terminal_chs_l"), // 左侧中文 0
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.terminal_pinyin_l"), // 左侧拼音 1
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.terminal_chs_r"), // 右侧中文 2
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.terminal_pinyin_r"), // 右侧拼音 3
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.train_type"), // 车种 4
                Component.translatable(
                        "container." + Main.MOD_ID + ".laquered_board_edit_menu.no"), // 车号 5
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.belt_color"), // 条带颜色 6
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.chs_color"), // 中文颜色 7
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.pinyin_color"), // 拼音颜色 8
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.x_offset"), // 拼音颜色 9
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.confirm"), // 确认 10
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.edit_color"), // 编辑颜色 11
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.cancel"), // 取消 12
                Component.translatable(
                        "container." + Main.MOD_ID + ".laquered_board_edit_menu.edit_text") // 字体 13
            };

    private static int editBoxWidth = 200;
    private static int editBoxHeight = 15;
    private static int offsetX = 70;
    private static int offsetY = 15;

    private boolean editingColors = false;

    private LacqueredBoardEntity entity;

    EditBox terminalChsL,
            terminalPinyinL,
            terminalChsR,
            terminalPinyinR,
            trainType,
            No,
            beltColor,
            forGroundColor,
            pinyinColor,
            xOffset;

    private Button[] buttons = new Button[3];

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
        loadEntity(pMenu.getLbe());
    }

    /** 加载静态 Container 的类，在游戏启动时自动加载 */
    public void init() {
        int windowWidth = this.getMinecraft().screen.width;
        int centreX = windowWidth / 2;

        int xPos = centreX - editBoxWidth / 2 - offsetX;

        Minecraft minecraft = Minecraft.getInstance();

        terminalChsL =
                new EditBox(
                        minecraft.font, xPos, 45 - offsetY, editBoxWidth, editBoxHeight, TEXT[0]);
        terminalPinyinL =
                new EditBox(
                        minecraft.font, xPos, 75 - offsetY, editBoxWidth, editBoxHeight, TEXT[1]);
        terminalChsR =
                new EditBox(
                        minecraft.font, xPos, 105 - offsetY, editBoxWidth, editBoxHeight, TEXT[2]);
        terminalPinyinR =
                new EditBox(
                        minecraft.font, xPos, 135 - offsetY, editBoxWidth, editBoxHeight, TEXT[3]);
        trainType =
                new EditBox(
                        minecraft.font, xPos, 165 - offsetY, editBoxWidth, editBoxHeight, TEXT[4]);
        No = new EditBox(minecraft.font, xPos, 195 - offsetY, editBoxWidth, editBoxHeight, TEXT[5]);

        terminalChsL.setValue(entity.getMessage(0).getString());
        terminalPinyinL.setValue(entity.getMessage(2).getString());
        terminalChsR.setValue(entity.getMessage(1).getString());
        terminalPinyinR.setValue(entity.getMessage(3).getString());
        trainType.setValue(entity.getMessage(4).getString());
        No.setValue(entity.getMessage(5).getString());

        beltColor =
                new EditBox(
                        minecraft.font, xPos, 45 - offsetY, editBoxWidth, editBoxHeight, TEXT[6]);
        forGroundColor =
                new EditBox(
                        minecraft.font, xPos, 75 - offsetY, editBoxWidth, editBoxHeight, TEXT[7]);
        pinyinColor =
                new EditBox(
                        minecraft.font, xPos, 105 - offsetY, editBoxWidth, editBoxHeight, TEXT[8]);
        xOffset =
                new EditBox(
                        minecraft.font, xPos, 135 - offsetY, editBoxWidth, editBoxHeight, TEXT[9]);

        beltColor.setValue(entity.getBackGroundColor() + "");
        forGroundColor.setValue(entity.getForGroundColor() + "");
        pinyinColor.setValue(entity.getBeltForGroundColor() + "");
        xOffset.setValue(entity.getXOffset() + "");

        renderEditPanel1();

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
                                    TEXT[i + 10],
                                    b -> {
                                        if (b.equals(buttons[0])) {
                                            if (entity != null) {
                                                boolean error = false;
                                                int beColor = entity.getBackGroundColor(),
                                                        foColor = entity.getForGroundColor(),
                                                        pyColor = entity.getBeltForGroundColor();
                                                double offset = entity.getXOffset();
                                                try {
                                                    beltColor.setValue(
                                                            beltColor
                                                                    .getValue()
                                                                    .replaceAll(" ", ""));
                                                    forGroundColor.setValue(
                                                            forGroundColor
                                                                    .getValue()
                                                                    .replaceAll(" ", ""));
                                                    pinyinColor.setValue(
                                                            pinyinColor
                                                                    .getValue()
                                                                    .replaceAll(" ", ""));
                                                    xOffset.setValue(
                                                            xOffset.getValue().replaceAll(" ", ""));
                                                    if (beltColor.getValue().equals("")) {
                                                        beltColor.setValue("15216648");
                                                    }
                                                    if (forGroundColor.getValue().equals("")) {
                                                        forGroundColor.setValue("0");
                                                    }
                                                    if (pinyinColor.getValue().equals("")) {
                                                        pinyinColor.setValue("0xffffff");
                                                    }
                                                    if (xOffset.getValue().equals("")) {
                                                        xOffset.setValue("0.0");
                                                    }
                                                    if (beltColor
                                                            .getValue()
                                                            .toLowerCase(Locale.ROOT)
                                                            .startsWith("0x")) {
                                                        beColor =
                                                                Integer.parseInt(
                                                                        beltColor
                                                                                .getValue()
                                                                                .toLowerCase(
                                                                                        Locale.ROOT)
                                                                                .substring(2),
                                                                        16);
                                                    } else {
                                                        beColor =
                                                                Integer.parseInt(
                                                                        beltColor.getValue());
                                                    }
                                                    if (forGroundColor
                                                            .getValue()
                                                            .toLowerCase(Locale.ROOT)
                                                            .startsWith("0x")) {
                                                        foColor =
                                                                Integer.parseInt(
                                                                        forGroundColor
                                                                                .getValue()
                                                                                .toLowerCase(
                                                                                        Locale.ROOT)
                                                                                .substring(2),
                                                                        16);
                                                    } else {
                                                        foColor =
                                                                Integer.parseInt(
                                                                        forGroundColor.getValue());
                                                    }
                                                    if (pinyinColor
                                                            .getValue()
                                                            .toLowerCase(Locale.ROOT)
                                                            .startsWith("0x")) {
                                                        pyColor =
                                                                Integer.parseInt(
                                                                        pinyinColor
                                                                                .getValue()
                                                                                .toLowerCase(
                                                                                        Locale.ROOT)
                                                                                .substring(2),
                                                                        16);
                                                    } else {
                                                        pyColor =
                                                                Integer.parseInt(
                                                                        pinyinColor.getValue());
                                                    }
                                                    offset = Double.parseDouble(xOffset.getValue());
                                                } catch (Exception e) {
                                                    error = true;
                                                }
                                                if (!error) {
                                                    entity.setColors(beColor, foColor, pyColor);
                                                    entity.setXOffset(offset);
                                                    entity.setMessages(
                                                            new String[] {
                                                                terminalChsL.getValue(),
                                                                terminalChsR.getValue(),
                                                                terminalPinyinL.getValue(),
                                                                terminalPinyinR.getValue(),
                                                                trainType.getValue(),
                                                                No.getValue()
                                                            });
                                                    entity.markUpdated(true);
                                                    onClose();
                                                }
                                            }
                                        } else if (b.equals(buttons[1])) {
                                            editingColors = !editingColors;
                                            if (editingColors) {
                                                buttons[1].setMessage(TEXT[13]);
                                                removePanel1();
                                                renderColorEditPanel();
                                            } else {
                                                buttons[1].setMessage(TEXT[11]);
                                                removeColorEditPanel();
                                                renderEditPanel1();
                                            }
                                        } else if (b.equals(buttons[2])) {
                                            onClose();
                                        }
                                    }));
        }
    }

    public void renderEditPanel1() {
        addRenderableWidget(terminalChsL);
        addRenderableWidget(terminalPinyinL);
        addRenderableWidget(terminalChsR);
        addRenderableWidget(terminalPinyinR);
        addRenderableWidget(trainType);
        addRenderableWidget(No);
    }

    public void removePanel1() {
        removeWidget(terminalChsL);
        removeWidget(terminalChsR);
        removeWidget(terminalPinyinL);
        removeWidget(terminalPinyinR);
        removeWidget(trainType);
        removeWidget(No);
    }

    public void renderColorEditPanel() {
        addRenderableWidget(beltColor);
        addRenderableWidget(forGroundColor);
        addRenderableWidget(pinyinColor);
        addRenderableWidget(xOffset);
    }

    public void removeColorEditPanel() {
        removeWidget(beltColor);
        removeWidget(forGroundColor);
        removeWidget(pinyinColor);
        removeWidget(xOffset);
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        int windowWidth = this.getMinecraft().screen.width;

        int centreX = windowWidth / 2;

        if (!editingColors) {
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[0],
                    centreX - this.font.width(TEXT[0].getString()) / 2 - offsetX,
                    33 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[1],
                    centreX - this.font.width(TEXT[1].getString()) / 2 - offsetX,
                    63 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[2],
                    centreX - this.font.width(TEXT[2].getString()) / 2 - offsetX,
                    93 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[3],
                    centreX - this.font.width(TEXT[3].getString()) / 2 - offsetX,
                    123 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[4],
                    centreX - this.font.width(TEXT[4].getString()) / 2 - offsetX,
                    153 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[5],
                    centreX - this.font.width(TEXT[5].getString()) / 2 - offsetX,
                    183 - offsetY,
                    0xFFFFFF);
        } else {
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[6],
                    centreX - this.font.width(TEXT[6].getString()) / 2 - offsetX,
                    33 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[7],
                    centreX - this.font.width(TEXT[7].getString()) / 2 - offsetX,
                    63 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[8],
                    centreX - this.font.width(TEXT[8].getString()) / 2 - offsetX,
                    93 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[9],
                    centreX - this.font.width(TEXT[9].getString()) / 2 - offsetX,
                    123 - offsetY,
                    0xFFFFFF);
        }
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {}

    /**
     * 装载对应的方块实体
     *
     * @param entity 装载实体
     */
    public void loadEntity(LacqueredBoardEntity entity) {
        this.entity = entity;
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
