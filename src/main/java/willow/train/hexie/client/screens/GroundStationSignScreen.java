package willow.train.hexie.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import willow.train.hexie.menu.GroundStationSignMenu;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.signs.GroundStationSignUpdatePacket;

public class GroundStationSignScreen extends AbstractContainerScreen<GroundStationSignMenu> {
    private static final String[] LABELS = {"station_name", "pinyin", "previous", "next"};
    private int fieldWidth = 200;
    private final EditBox[] fields = new EditBox[4];
    public GroundStationSignScreen(GroundStationSignMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 340; imageHeight = 160;
    }
    @Override protected void init() {
        String[] values = new String[4];
        for (int i = 0; i < 4; i++) values[i] = fields[i] == null ? menu.initialFields[i] : fields[i].getValue();
        imageWidth = Math.min(360, width - 16);
        fieldWidth = Math.min(200, imageWidth - 130);
        super.init();
        for (int i = 0; i < 4; i++) {
            fields[i] = new EditBox(font, leftPos, topPos + 18 + i * 30, fieldWidth, 15,
                    Component.translatable("gui.hexie.ground_station_sign." + LABELS[i]));
            fields[i].setMaxLength(64); fields[i].setValue(values[i]); addRenderableWidget(fields[i]);
        }
        addRenderableWidget(new Button(leftPos + 2, topPos + 138, fieldWidth / 3 - 4, 15,
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.confirm"), button -> {
            String[] edited = new String[4]; for (int i = 0; i < 4; i++) edited[i] = fields[i].getValue();
            HexieNetworkHandler.sendToServer(new GroundStationSignUpdatePacket(menu.pos, edited)); onClose();
        }));
        addRenderableWidget(new Button(leftPos + fieldWidth / 3 + 2, topPos + 138, fieldWidth / 3 - 4, 15,
                Component.translatable("gui.hexie.ground_station_sign.switch_font"), button -> {
            if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }));
        addRenderableWidget(new Button(leftPos + fieldWidth * 2 / 3 + 2, topPos + 138, fieldWidth / 3 - 4, 15,
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.cancel"), button -> onClose()));
        setInitialFocus(fields[0]);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos + fieldWidth + 16, available = imageWidth - fieldWidth - 16;
        float scale = Math.min(available / 38f, 150 / 43f);
        float center = x + available / 2f, bottom = topPos + 154;
        graphics.flush();
        graphics.enableScissor(x, topPos, leftPos + imageWidth, topPos + imageHeight);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(center, bottom, 100);
        pose.scale(scale, -scale, scale);
        // Draw the real block model and its textures head-on, in the same model coordinates as the text.
        pose.pushPose();
        pose.scale(16, 16, 16);
        pose.translate(-.5, 0, -.5);
        com.mojang.blaze3d.platform.Lighting.setupForFlatItems();
        minecraft.getBlockRenderer().renderSingleBlock(
                willow.train.hexie.init.block.BlockInit.GROUND_STATION_SIGN.get().defaultBlockState(),
                pose, graphics.bufferSource(), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        graphics.flush();
        pose.popPose();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        pose.translate(0, 0, 1.025f);
        String[] values = new String[4];
        for (int i = 0; i < 4; i++) values[i] = fields[i] == null ? menu.initialFields[i] : fields[i].getValue();
        int style = minecraft.level != null && minecraft.level.getBlockEntity(menu.pos)
                instanceof willow.train.hexie.blockentity.GroundStationSignEntity sign ? sign.fontStyle() : 0;
        willow.train.hexie.client.render.blockentity.GroundStationSignRenderer.renderFace(values, style, false,
                pose, graphics.bufferSource(), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        graphics.flush();
        pose.popPose();
        graphics.disableScissor();
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < 4; i++) {
            Component label = Component.translatable("gui.hexie.ground_station_sign." + LABELS[i]);
            willow.train.hexie.client.util.gui.GuiText.draw(graphics, font, label,
                    fieldWidth / 2f - font.width(label) / 2f, 6 + i * 30, 0xFFFFFF);
        }
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Text fields get inventory-key input before the container closes on that key.
        if (key != 256 && getFocused() instanceof EditBox box && box.keyPressed(key, scanCode, modifiers)) return true;
        if (key != 256 && getFocused() instanceof EditBox && minecraft.options.keyInventory.matches(key, scanCode)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }
}
