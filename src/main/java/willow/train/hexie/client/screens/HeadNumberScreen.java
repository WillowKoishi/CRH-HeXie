package willow.train.hexie.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import willow.train.hexie.Main;
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.client.util.gui.Button;
import willow.train.hexie.client.util.gui.GuiText;
import willow.train.hexie.menu.HeadNumberMenu;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.signs.HeadNumberUpdatePacket;

public final class HeadNumberScreen extends TextInputScreen<HeadNumberMenu> {
    private static final int EDIT_BOX_WIDTH = 200;
    private static final int EDIT_BOX_HEIGHT = 20;
    private static final int OFFSET_X = 70;
    private static final int OFFSET_Y = 15;
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/gui.png");
    private EditBox number;

    public HeadNumberScreen(HeadNumberMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        String value = number == null ? menu.initialNumber : number.getValue();
        int x = width / 2 - EDIT_BOX_WIDTH / 2 - OFFSET_X;
        number = new EditBox(font, x, 45 - OFFSET_Y, EDIT_BOX_WIDTH, EDIT_BOX_HEIGHT, title);
        number.setMaxLength(CRH2HeadEntity.MAX_LENGTH);
        number.setValue(value);
        addRenderableWidget(number);
        addRenderableWidget(new Button(x + 5, 200, EDIT_BOX_WIDTH / 3 - 10, EDIT_BOX_HEIGHT,
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.confirm"), button -> {
                    HexieNetworkHandler.sendToServer(new HeadNumberUpdatePacket(menu.pos, number.getValue()).withMenuId(menu.containerId));
                    onClose();
                }));
        addRenderableWidget(new Button(x + EDIT_BOX_WIDTH / 3 + 5, 200,
                EDIT_BOX_WIDTH / 3 - 10, EDIT_BOX_HEIGHT,
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.cancel"), button -> onClose()));
        setInitialFocus(number);
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
        GuiText.draw(graphics, font, title, width / 2 - font.width(title) / 2 - OFFSET_X,
                35 - OFFSET_Y, 0xFFFFFF);
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
