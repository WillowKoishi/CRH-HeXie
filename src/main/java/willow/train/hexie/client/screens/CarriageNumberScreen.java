package willow.train.hexie.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.Main;
import willow.train.hexie.client.util.gui.Button;
import willow.train.hexie.client.util.gui.GuiText;
import willow.train.hexie.menu.CarriageNumberMenu;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.signs.CarriageNumberUpdatePacket;

/** Edit the local coach identifier without changing the attachment or timetable. */
public final class CarriageNumberScreen extends AbstractContainerScreen<CarriageNumberMenu> {
    // Use the existing single-field HeadNumberScreen editor geometry and shared background.
    private static final int EDIT_BOX_WIDTH = 200;
    private static final int EDIT_BOX_HEIGHT = 20;
    private static final int OFFSET_X = 70;
    private static final int OFFSET_Y = 15;
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/gui.png");
    private EditBox number;

    public CarriageNumberScreen(CarriageNumberMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void init() {
        String value = number == null ? menu.initialNumber : number.getValue();
        int x = width / 2 - EDIT_BOX_WIDTH / 2 - OFFSET_X;
        number = new EditBox(font, x, 45 - OFFSET_Y, EDIT_BOX_WIDTH, EDIT_BOX_HEIGHT, title);
        number.setMaxLength(2);
        number.setFilter(text -> text.length() <= 2 && text.chars().allMatch(c -> c >= '0' && c <= '9'));
        number.setValue(value);
        addRenderableWidget(number);
        addRenderableWidget(new Button(x + 5, 200, EDIT_BOX_WIDTH / 3 - 10, EDIT_BOX_HEIGHT,
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.confirm"), button -> save()));
        addRenderableWidget(new Button(x + EDIT_BOX_WIDTH / 3 + 5, 200,
                EDIT_BOX_WIDTH / 3 - 10, EDIT_BOX_HEIGHT,
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.cancel"), button -> onClose()));
        setInitialFocus(number);
    }

    private void save() {
        HexieNetworkHandler.sendToServer(new CarriageNumberUpdatePacket(menu.containerId, number.getValue()));
        onClose();
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, TEXTURE);
        graphics.blit(TEXTURE, (width - imageWidth) / 2, (height - imageHeight) / 2,
                0, 0, imageWidth, imageHeight);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiText.draw(graphics, font, title, width / 2 - font.width(title) / 2 - OFFSET_X,
                35 - OFFSET_Y, 0xFFFFFF);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override public boolean isPauseScreen() { return true; }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        if (key != GLFW.GLFW_KEY_ESCAPE && getFocused() instanceof EditBox box && box.keyPressed(key, scanCode, modifiers)) return true;
        if (key != GLFW.GLFW_KEY_ESCAPE && getFocused() instanceof EditBox && minecraft.options.keyInventory.matches(key, scanCode)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }
}
