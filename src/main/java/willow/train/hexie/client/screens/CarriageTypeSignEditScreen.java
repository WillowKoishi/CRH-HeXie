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
import willow.train.hexie.menu.CarriageTypeSignEditMenu;
import willow.train.hexie.client.screens.widget.ImageButton;
import willow.train.hexie.client.util.GuiImageCache;
import willow.train.hexie.blockentity.CarriageTypeSignEntity;
import willow.train.hexie.client.util.RenderablePicture;

import java.util.Locale;
import java.util.Random;

public class CarriageTypeSignEditScreen extends AbstractContainerScreen<CarriageTypeSignEditMenu> {

    private static final Component[] TEXT =
            new Component[] {
                Component.translatable(
                                "container."
                                        + Main.MOD_ID
                                        + ".carriage_type_sign_edit_menu.type_chs")
                        , // 类型(中文) 0
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.type_chs_py"), // 类型(拼音) 1
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.type_abbr"), // 类型(缩写) 2
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.sub_type"), // 子类 3
                Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.no"), // 编号 4
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.color"), // 颜色label 5
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.predefine_color"), // 预定义颜色 6
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.confirm"), // 确认 7
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.edit_color"), // 编辑颜色 8
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.cancel"), // 取消 9
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".laquered_board_edit_menu.edit_text") // 编辑字体 10
            };

    Component random_confirm =
            Component.translatable(
                    "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.random_confirm");
    Component random_cancel =
            Component.translatable(
                    "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.random_cancel");
    Component random_generate =
            Component.translatable(
                    "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.random_generate");
    Component random_title_l =
            Component.translatable(
                    "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.random_title_left");
    Component random_title_c =
            Component.translatable(
                    "container."
                            + Main.MOD_ID
                            + ".carriage_type_sign_edit_menu.random_title_center");
    Component random_title_r =
            Component.translatable(
                    "container."
                            + Main.MOD_ID
                            + ".carriage_type_sign_edit_menu.random_title_right");
    Component[] random_tooltips =
            new Component[] {
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.random_tooltip_title"),
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.random_tooltip_yz"),
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.random_tooltip_rz"),
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.random_tooltip_yw"),
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.random_tooltip_rw"),
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.random_tooltip_xl"),
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.random_tooltip_ca"),
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.random_tooltip_kd"),
                Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.random_tooltip_yoz")
            };

    private boolean editColor = false;
    private boolean randomPanel = false;
    private static int editBoxWidth = 200;
    private static int editBoxHeight = 20;
    private static int offsetX = 70;
    private static int offsetY = 15;

    private CarriageTypeSignEntity entity;

    EditBox TypeChs, TypePinyin, TypeAbbr, SubType, No, Color, random_box, random_max, random_min;

    private Button[] buttons = new Button[3];
    private Button[] random_buttons = new Button[2];

    private Button[] colorButtons = new Button[7];

    private ImageButton random;

    private Button generate;

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/gui.png");

    public static final GuiImageCache.ImageData DICE_IMAGE = GuiImageCache.register("textures/gui/dice_button.png", 0);
    public final RenderablePicture dice;

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
        loadEntity(pMenu.getCtse());
        dice = new RenderablePicture(DICE_IMAGE);
    }

    /** 加载静态 Container 的类，在游戏启动时自动加载 */
    public void init() {
        int windowWidth = this.getMinecraft().screen.width;
        int centreX = windowWidth / 2;

        int xPos = centreX - editBoxWidth / 2 - offsetX;

        Minecraft minecraft = Minecraft.getInstance();
        TypeChs =
                new EditBox(
                        minecraft.font, xPos, 45 - offsetY, editBoxWidth, editBoxHeight, TEXT[0]);
        TypePinyin =
                new EditBox(
                        minecraft.font, xPos, 80 - offsetY, editBoxWidth, editBoxHeight, TEXT[1]);
        TypeAbbr =
                new EditBox(
                        minecraft.font, xPos, 115 - offsetY, editBoxWidth, editBoxHeight, TEXT[2]);
        SubType =
                new EditBox(
                        minecraft.font, xPos, 150 - offsetY, editBoxWidth, editBoxHeight, TEXT[3]);
        No =
                new EditBox(
                        minecraft.font,
                        xPos,
                        185 - offsetY,
                        editBoxWidth - 30,
                        editBoxHeight,
                        TEXT[4]);
        Color =
                new EditBox(
                        minecraft.font, xPos, 45 - offsetY, editBoxWidth, editBoxHeight, TEXT[6]);

        random_min =
                new EditBox(
                        minecraft.font,
                        xPos,
                        50 - offsetY,
                        editBoxWidth / 3 - 10,
                        editBoxHeight,
                        Component.empty());
        random_box =
                new EditBox(
                        minecraft.font,
                        xPos + editBoxWidth / 3 + 5,
                        50 - offsetY,
                        editBoxWidth / 3 - 10,
                        editBoxHeight,
                        Component.empty());
        random_max =
                new EditBox(
                        minecraft.font,
                        xPos + editBoxWidth * 2 / 3 + 10,
                        50 - offsetY,
                        editBoxWidth / 3 - 10,
                        editBoxHeight,
                        Component.empty());

        TypeChs.setValue(
                entity.getMessage(0, false).getString().equals("XXX")
                        ? ""
                        : entity.getMessage(0, false).getString());
        TypePinyin.setValue(
                entity.getMessage(1, false).getString().equals("XXXXXX")
                        ? ""
                        : entity.getMessage(1, false).getString());
        TypeAbbr.setValue(
                entity.getMessage(2, false).getString().equals("XX")
                        ? ""
                        : entity.getMessage(2, false).getString());
        SubType.setValue(
                entity.getMessage(3, false).getString().equals("XXX")
                        ? ""
                        : entity.getMessage(3, false).getString());
        No.setValue(
                entity.getMessage(4, false).getString().equals("XXXXXX")
                        ? ""
                        : entity.getMessage(4, false).getString());
        Color.setValue(entity.getColor() + "");
        random_box.setValue(No.getValue());

        for (int i = 0; i < colorButtons.length; i++) {
            colorButtons[i] =
                    new Button(
                            xPos + (i % 2) * editBoxWidth / 2 + 5,
                            90 - offsetY + ((int) (((float) i) / 2)) * 25,
                            editBoxWidth / 2 - 10,
                            editBoxHeight,
                            predefineColorMapping(i),
                            b -> {
                                for (int bx = 0; bx < colorButtons.length; bx++) {
                                    if (colorButtons[bx].equals(b)) {
                                        Color.setValue(predefineColorMapping2(bx) + "");
                                    }
                                }
                            });
        }

        generate =
                new Button(
                        xPos,
                        80 - offsetY,
                        editBoxWidth,
                        editBoxHeight,
                        random_generate,
                        b -> {
                            Random random1 = new Random();
                            // random_box.setValue(No.getValue());
                            try {
                                int min = 0;
                                int bound = 0;
                                switch (random_box.getValue().toLowerCase(Locale.ROOT)) {
                                    case "yz" -> {
                                        min = 300000;
                                        bound = 309999;
                                        break;
                                    } // 硬座
                                    case "rz" -> {
                                        min = 110000;
                                        bound = 119999;
                                        break;
                                    } // 软座
                                    case "yw" -> {
                                        min = 600000;
                                        bound = 699999;
                                        break;
                                    } // 硬卧
                                    case "rw" -> {
                                        min = 500000;
                                        bound = 599999;
                                        break;
                                    } // 软卧
                                    case "xl" -> {
                                        min = 200000;
                                        bound = 299999;
                                        break;
                                    } // 行李
                                    case "ca" -> {
                                        min = 800000;
                                        bound = 899999;
                                        break;
                                    } // 餐车
                                    case "kd" -> {
                                        min = 900000;
                                        bound = 999999;
                                        break;
                                    } // 空电
                                    case "uz" -> {
                                        min = 8000;
                                        bound = 8999;
                                        break;
                                    } // 邮政
                                    default -> {
                                        min = Integer.parseInt(random_min.getValue());
                                        bound = Integer.parseInt(random_max.getValue());
                                    }
                                }
                                random_box.setValue(random1.nextInt(min, bound) + "");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

        random_box.setValue(No.getValue());

        random =
                new ImageButton(
                        dice,
                        xPos + editBoxWidth - 25,
                        185 - offsetY,
                        20,
                        20,
                        b -> {
                            removeTextPanel();
                            removeMainButtons();
                            renderRandomPanel();
                            editColor = false;
                            randomPanel = true;
                        });

        for (int i = 0; i < 2; i++) {
            random_buttons[i] =
                    new Button(
                            xPos + i * editBoxWidth / 2 + 5,
                            230 - offsetY,
                            editBoxWidth / 2 - 10,
                            editBoxHeight,
                            i == 0 ? random_confirm : random_cancel,
                            b -> {
                                if (b.equals(random_buttons[0])) {
                                    try {
                                        Integer.parseInt(random_box.getValue());
                                    } catch (Exception e) {
                                        return;
                                    }
                                    No.setValue(random_box.getValue());
                                }
                                removeRandomPanel();
                                renderMainButtons();
                                renderTextPanel();
                                editColor = false;
                                randomPanel = false;
                            });
        }

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
                                    TEXT[i + 7],
                                    b -> {
                                        if (b.equals(buttons[0])) {
                                            if (entity != null) {
                                                boolean error = false;
                                                int c = entity.getColor();
                                                Color.setValue(
                                                        Color.getValue().replaceAll(" ", ""));
                                                if (Color.getValue().equals(""))
                                                    Color.setValue(entity.getColor() + "");
                                                try {
                                                    if (Color.getValue()
                                                            .toLowerCase(Locale.ROOT)
                                                            .startsWith("0x"))
                                                        c =
                                                                Integer.parseInt(
                                                                        Color.getValue()
                                                                                .toLowerCase(
                                                                                        Locale.ROOT)
                                                                                .substring(2),
                                                                        16);
                                                    else c = Integer.parseInt(Color.getValue());
                                                } catch (Exception e) {
                                                    error = true;
                                                }
                                                if (!error) {
                                                    entity.setColor(c);
                                                    if (!isEmpty(
                                                            TypeChs,
                                                            TypePinyin,
                                                            TypePinyin,
                                                            SubType,
                                                            No)) {
                                                        entity.setMessages(
                                                                new String[] {
                                                                    TypeChs.getValue(),
                                                                    TypePinyin.getValue(),
                                                                    TypeAbbr.getValue(),
                                                                    SubType.getValue(),
                                                                    No.getValue()
                                                                });
                                                    }
                                                    entity.markUpdated(true);
                                                    onClose();
                                                }
                                            }
                                        } else if (b.equals(buttons[1])) {
                                            editColor = !editColor;
                                            randomPanel = false;
                                            if (editColor) {
                                                b.setMessage(TEXT[10]);
                                                removeTextPanel();
                                                renderColorPanel();
                                            } else {
                                                b.setMessage(TEXT[8]);
                                                removeColorPanel();
                                                renderTextPanel();
                                            }
                                        } else if (b.equals(buttons[2])) {
                                            onClose();
                                        }
                                    }));
        }

        if (!editColor && !randomPanel) {
            renderTextPanel();
        } else if (editColor) {
            renderColorPanel();
            buttons[1].setMessage(TEXT[10]);
        } else if (randomPanel) {
            removeMainButtons();
            renderRandomPanel();
        }
    }

    public void renderMainButtons() {
        for (Button b : buttons) {
            addRenderableWidget(b);
        }
    }

    public void removeMainButtons() {
        for (Button b : buttons) {
            removeWidget(b);
        }
    }

    public void renderTextPanel() {
        addRenderableWidget(TypeChs);
        addRenderableWidget(TypePinyin);
        addRenderableWidget(TypeAbbr);
        addRenderableWidget(SubType);
        addRenderableWidget(No);
        addRenderableWidget(random);
    }

    public void removeTextPanel() {
        removeWidget(TypeChs);
        removeWidget(TypePinyin);
        removeWidget(TypeAbbr);
        removeWidget(SubType);
        removeWidget(No);
        removeWidget(random);
    }

    public void renderColorPanel() {
        addRenderableWidget(Color);
        for (Button b : colorButtons) {
            addRenderableWidget(b);
        }
    }

    public void removeColorPanel() {
        removeWidget(Color);
        for (Button b : colorButtons) {
            removeWidget(b);
        }
    }

    public void renderRandomPanel() {
        addRenderableWidget(random_min);
        addRenderableWidget(random_box);
        addRenderableWidget(random_max);
        addRenderableWidget(generate);
        addRenderableWidget(random_buttons[0]);
        addRenderableWidget(random_buttons[1]);
    }

    public void removeRandomPanel() {
        removeWidget(random_min);
        removeWidget(random_box);
        removeWidget(random_max);
        removeWidget(generate);
        removeWidget(random_buttons[0]);
        removeWidget(random_buttons[1]);
    }

    public Component predefineColorMapping(int x) {
        switch (x) {
            case 0 -> {
                return Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.color.yellow");
            }
            case 1 -> {
                return Component.translatable(
                        "container."
                                + Main.MOD_ID
                                + ".carriage_type_sign_edit_menu.color.yellow_2");
            }
            case 2 -> {
                return Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.color.red");
            }
            case 3 -> {
                return Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.color.blue");
            }
            case 4 -> {
                return Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.color.blue_2");
            }
            case 5 -> {
                return Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.color.blue_3");
            }
            case 6 -> {
                return Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.color.black");
            }
            default -> {
                return Component.translatable(
                        "container." + Main.MOD_ID + ".carriage_type_sign_edit_menu.color.yellow");
            }
        }
    }

    public int predefineColorMapping2(int x) {
        switch (x) {
            case 0 -> {
                return CarriageTypeSignEntity.YELLOW;
            }
            case 1 -> {
                return CarriageTypeSignEntity.YELLOW2;
            }
            case 2 -> {
                return CarriageTypeSignEntity.RED;
            }
            case 3 -> {
                return CarriageTypeSignEntity.BLUE;
            }
            case 4 -> {
                return CarriageTypeSignEntity.BLUE2;
            }
            case 5 -> {
                return CarriageTypeSignEntity.BLUE3;
            }
            case 6 -> {
                return CarriageTypeSignEntity.BLACK;
            }
            default -> {
                return CarriageTypeSignEntity.YELLOW;
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        int windowWidth = this.getMinecraft().screen.width;

        int centreX = windowWidth / 2;

        if (!editColor && !randomPanel) {
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
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[2],
                    centreX - this.font.width(TEXT[2].getString()) / 2 - offsetX,
                    105 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[3],
                    centreX - this.font.width(TEXT[3].getString()) / 2 - offsetX,
                    140 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[4],
                    centreX - this.font.width(TEXT[4].getString()) / 2 - offsetX,
                    175 - offsetY,
                    0xFFFFFF);
        } else if (editColor) {
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[5],
                    centreX - this.font.width(TEXT[5].getString()) / 2 - offsetX,
                    35 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    TEXT[6],
                    centreX - this.font.width(TEXT[6].getString()) / 2 - offsetX,
                    80 - offsetY,
                    0xFFFFFF);
        } else if (randomPanel) {
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    random_title_l,
                    random_min.getX()
                            + editBoxWidth / 6
                            - this.font.width(random_title_l.getString()) / 2
                            - 5,
                    40 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    random_title_c,
                    random_box.getX()
                            + editBoxWidth / 6
                            - this.font.width(random_title_c.getString()) / 2
                            - 5,
                    40 - offsetY,
                    0xFFFFFF);
            willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                    random_title_r,
                    random_max.getX()
                            + editBoxWidth / 6
                            - this.font.width(random_title_r.getString()) / 2
                            - 5,
                    40 - offsetY,
                    0xFFFFFF);
            for (int i = 0; i < random_tooltips.length; i++) {
                willow.train.hexie.client.util.gui.GuiText.draw(pGuiGraphics, this.font,
                        random_tooltips[i],
                        centreX - this.font.width(random_tooltips[i].getString()) / 2 - offsetX,
                        105 - offsetY + i * 14,
                        0xFFFFFF);
            }
        }
    }

    /**
     * 装载对应的方块实体
     *
     * @param entity 装载实体
     */
    public void loadEntity(CarriageTypeSignEntity entity) {
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

    private boolean isEmpty(EditBox... boxes) {
        for (EditBox c : boxes) {
            if (!c.getValue().equals("")) {
                return false;
            }
        }
        return true;
    }
}
