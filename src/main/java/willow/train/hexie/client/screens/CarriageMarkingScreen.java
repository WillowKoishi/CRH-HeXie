package willow.train.hexie.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import willow.train.hexie.Main;
import willow.train.hexie.client.util.gui.Button;
import willow.train.hexie.client.util.gui.GuiText;
import willow.train.hexie.menu.CarriageMarkingMenu;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.signs.CarriageMarkingUpdatePacket;

public final class CarriageMarkingScreen extends TextInputScreen<CarriageMarkingMenu> {
    // Match CarriageTypeSignEditScreen's editor geometry and controls.
    private static final int EDIT_BOX_WIDTH = 200;
    private static final int EDIT_BOX_HEIGHT = 20;
    private static final int OFFSET_X = 70;
    private static final int OFFSET_Y = 15;
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/gui.png");
    private static final String[] LABELS = {"number", "chinese", "english"};
    private final EditBox[] fields = new EditBox[3];

    public CarriageMarkingScreen(CarriageMarkingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        String[] values = values();
        int x = width / 2 - EDIT_BOX_WIDTH / 2 - OFFSET_X;
        for (int i = 0; i < fields.length; i++) {
            fields[i] = new EditBox(font, x, 45 - OFFSET_Y + i * 35,
                    EDIT_BOX_WIDTH, EDIT_BOX_HEIGHT, label(i));
            fields[i].setMaxLength(64);
            fields[i].setValue(values[i]);
            addRenderableWidget(fields[i]);
        }
        addRenderableWidget(new Button(x + 5, 200, EDIT_BOX_WIDTH / 3 - 10, EDIT_BOX_HEIGHT,
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.confirm"), button -> {
                    String[] edited = values();
                    HexieNetworkHandler.sendToServer(new CarriageMarkingUpdatePacket(
                            menu.pos, edited[0], edited[1], edited[2], menu.initialLeft).withMenuId(menu.containerId));
                    onClose();
                }));
        addRenderableWidget(new Button(x + EDIT_BOX_WIDTH / 3 + 5, 200,
                EDIT_BOX_WIDTH / 3 - 10, EDIT_BOX_HEIGHT,
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.cancel"), button -> onClose()));
        setInitialFocus(fields[0]);
    }

    private static Component label(int index) {
        return Component.translatable("gui.hexie.carriage_marking." + LABELS[index]);
    }

    private String[] values() {
        String[] values = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            values[i] = fields[i] == null ? menu.initialFields[i] : fields[i].getValue();
        }
        return values;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, TEXTURE);
        graphics.blit(TEXTURE, (width - imageWidth) / 2, (height - imageHeight) / 2,
                0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < fields.length; i++) {
            Component label = label(i);
            GuiText.draw(graphics, font, label, width / 2 - font.width(label) / 2 - OFFSET_X,
                    35 - OFFSET_Y + i * 35, 0xFFFFFF);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

}
