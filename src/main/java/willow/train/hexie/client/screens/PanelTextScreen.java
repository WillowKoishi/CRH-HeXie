package willow.train.hexie.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import willow.train.hexie.blocks.parts.PanelText;
import willow.train.hexie.client.util.gui.Button;
import willow.train.hexie.client.util.gui.GuiText;
import willow.train.hexie.menu.PanelTextMenu;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.signs.PanelTextUpdatePacket;

import java.util.List;
import java.math.BigDecimal;

public final class PanelTextScreen extends AbstractContainerScreen<PanelTextMenu> {
    private static final List<String> FONTS = List.of("hexie:carriage_marking_chinese", "hexie:route_service",
            "hexie:station_name_serif", "hexie:head_number", "hexie:vehicle_code", "minecraft:default");
    private static final String[] SETTINGS = {"size", "x", "y", "spacing"};
    private final EditBox[] fields = new EditBox[SETTINGS.length];
    private int fontIndex;
    private static final int CONTROL_WIDTH = 212;
    private EditBox line;
    private Button confirm;

    public PanelTextScreen(PanelTextMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        PanelText initial = menu.initial;
        fontIndex = Math.max(0, FONTS.indexOf(initial.font()));
        imageWidth = 212;
        imageHeight = 170;
    }

    @Override protected void init() {
        String text = line == null ? menu.initial.text() : line.getValue();
        imageWidth = Math.min(440, width - 12);
        super.init();
        line = new EditBox(font, leftPos, topPos + 14, CONTROL_WIDTH, 18, title);
        line.setMaxLength(128);
        line.setFilter(value -> value.codePointCount(0, value.length()) <= 64);
        line.setValue(text);
        addRenderableWidget(line);
        for (int i = 0; i < SETTINGS.length; i++) {
            int row = i, y = topPos + 38 + i * 21;
            addRenderableWidget(new Button(leftPos + 108, y, 20, 18, Component.literal("−"), b -> step(row, -1)));
            addRenderableWidget(new Button(leftPos + CONTROL_WIDTH - 20, y, 20, 18, Component.literal("+"), b -> step(row, 1)));
            String value = fields[i] == null ? initialValue(i) : fields[i].getValue();
            fields[i] = new EditBox(font, leftPos + 130, y, 60, 18,
                    Component.translatable("gui.hexie.panel_text." + SETTINGS[i]));
            fields[i].setValue(value);
            addRenderableWidget(fields[i]);
        }
        addRenderableWidget(new Button(leftPos + 68, topPos + 123, CONTROL_WIDTH - 68, 18, fontName(), b -> {
            fontIndex = (fontIndex + 1) % FONTS.size();
            b.setMessage(fontName());
        }));
        confirm = addRenderableWidget(new Button(leftPos, topPos + 150, 100, 18,
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.confirm"), b -> {
            var label = draft();
            HexieNetworkHandler.sendToServer(new PanelTextUpdatePacket(menu.containerId, label));
            onClose();
        }));
        addRenderableWidget(new Button(leftPos + 112, topPos + 150, 100, 18,
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.cancel"), b -> onClose()));
        for (EditBox field : fields) field.setResponder(value -> confirm.active = draft() != null);
        confirm.active = draft() != null;
        setInitialFocus(line);
    }

    private Component fontName() {
        return Component.translatable("gui.hexie.panel_text.font." + FONTS.get(fontIndex).split(":")[1]);
    }
    private String initialValue(int row) {
        return switch (row) {
            case 0 -> Float.toString(menu.initial.size());
            case 1 -> Double.toString(menu.initial.x() / 4d);
            case 2 -> Double.toString(menu.initial.y() / 4d);
            default -> Integer.toString(menu.initial.spacing());
        };
    }

    private void step(int row, int delta) {
        double value;
        try { value = Double.parseDouble(fields[row].getValue()); }
        catch (NumberFormatException ignored) { value = Double.parseDouble(initialValue(row)); }
        double next = value + delta * (row == 3 ? 1 : .25);
        if (Double.isFinite(next) && (row != 0 || next > 0))
            fields[row].setValue(BigDecimal.valueOf(next).stripTrailingZeros().toString());
    }

    private PanelText draft() {
        try {
            float size = Float.parseFloat(fields[0].getValue());
            if (!Float.isFinite(size) || size <= 0) return null;
            int x = quarterUnits(fields[1].getValue());
            int y = quarterUnits(fields[2].getValue());
            int spacing = new BigDecimal(fields[3].getValue()).intValueExact();
            return new PanelText(line.getValue(), size, x, y, FONTS.get(fontIndex), spacing);
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
    }

    private static int quarterUnits(String value) {
        return new BigDecimal(value).multiply(BigDecimal.valueOf(4)).intValueExact();
    }

    @Override protected void renderBg(GuiGraphics graphics, float tick, int mouseX, int mouseY) {
        if (!(menu.panelState.getBlock() instanceof willow.train.hexie.blocks.parts.VehicleMarkingSurface panel)) return;
        var state = menu.panelState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                net.minecraft.core.Direction.NORTH);
        var bounds = panel.markingBounds(state);
        int left = leftPos + CONTROL_WIDTH + 8, right = leftPos + imageWidth;
        int top = topPos + 14, bottom = topPos + imageHeight;
        if (right <= left) return;
        float scale = (float)Math.min((right - left) / (bounds.getXsize() + .5),
                (bottom - top) / (bounds.getYsize() + .5));
        graphics.flush();
        graphics.enableScissor(left, top, right, bottom);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate((left + right) * .5, (top + bottom) * .5, 100);
        pose.scale(scale, -scale, scale);
        pose.translate(-bounds.getCenter().x, -bounds.getCenter().y, 0);
        int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        com.mojang.blaze3d.platform.Lighting.setupForFlatItems();
        minecraft.getBlockRenderer().renderSingleBlock(state, pose, graphics.bufferSource(), light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        graphics.flush();
        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        var label = draft();
        if (label != null) willow.train.hexie.client.render.blockentity.CarriageMarkingRenderer.drawLabel(
                label, panel, bounds, menu.slot, pose, graphics.bufferSource(), light);
        graphics.flush();
        pose.popPose();
        graphics.disableScissor();
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiText.draw(graphics, font, title, 0, 1, 0xffffff);
        for (int i = 0; i < SETTINGS.length; i++) {
            int y = 43 + i * 21;
            GuiText.draw(graphics, font, Component.translatable("gui.hexie.panel_text." + SETTINGS[i]), 0, y, 0xffffff);
        }
        GuiText.draw(graphics, font, Component.translatable("gui.hexie.panel_text.font"), 0, 128, 0xffffff);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != 256 && getFocused() instanceof EditBox field && field.keyPressed(key, scanCode, modifiers)) return true;
        if (key != 256 && getFocused() instanceof EditBox && minecraft.options.keyInventory.matches(key, scanCode)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }
}
