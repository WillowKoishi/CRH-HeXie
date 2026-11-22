package willow.train.hexie.client.render.ss7d;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import willow.train.hexie.blocks.locos.ss7d.SS7DHeadEntity;
import willow.train.hexie.blocks.locos.ss7d.SS7DNumberMenu;
import willow.train.hexie.blocks.locos.ss7d.SS7DNumberUpdatePacket;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.client.screens.TextInputScreen;

public final class SS7DNumberScreen extends TextInputScreen<SS7DNumberMenu> {
    private EditBox digits;
    private EditBox bureau;
    private EditBox depot;
    private Checkbox hideDepot;
    private Checkbox secondEnd;

    public SS7DNumberScreen(SS7DNumberMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 184;
        imageHeight = 174;
    }

    @Override protected void init() {
        super.init();
        String text = digits == null ? menu.initialDigits : digits.getValue();
        String bureauText = bureau == null ? menu.initialBureau : bureau.getValue();
        String depotText = depot == null ? menu.initialDepot : depot.getValue();
        boolean hidden = hideDepot == null ? !menu.initialDepotVisible : hideDepot.selected();
        boolean endTwo = secondEnd == null ? menu.initialSecondEnd : secondEnd.selected();
        digits = new EditBox(font, leftPos + 64, topPos + 28, 98, 20, title);
        digits.setMaxLength(4);
        digits.setFilter(s -> s.chars().allMatch(c -> c >= '0' && c <= '9'));
        digits.setValue(text);
        addRenderableWidget(digits);
        bureau = new EditBox(font, leftPos + 12, topPos + 64, 62, 20,
                Component.translatable("gui.hexie.ss7d_bureau"));
        bureau.setMaxLength(SS7DHeadEntity.MAX_UNIT_LENGTH);
        bureau.setFilter(SS7DHeadEntity::validUnit);
        bureau.setHint(Component.literal("X"));
        bureau.setValue(bureauText);
        addRenderableWidget(bureau);
        depot = new EditBox(font, leftPos + 98, topPos + 64, 62, 20,
                Component.translatable("gui.hexie.ss7d_depot"));
        depot.setMaxLength(SS7DHeadEntity.MAX_UNIT_LENGTH);
        depot.setFilter(SS7DHeadEntity::validUnit);
        depot.setHint(Component.literal("X"));
        depot.setValue(depotText);
        addRenderableWidget(depot);
        hideDepot = addRenderableWidget(Checkbox.builder(
                        Component.translatable("gui.hexie.ss7d_hide_depot"), font)
                .pos(leftPos + 12, topPos + 95).selected(hidden).build());
        secondEnd = addRenderableWidget(Checkbox.builder(
                        Component.translatable("gui.hexie.ss7d_second_end"), font)
                .pos(leftPos + 12, topPos + 117).selected(endTwo).build());
        var confirm = addRenderableWidget(Button.builder(
                Component.translatable("container.hexie.carriage_type_sign_edit_menu.confirm"), b -> {
                    String value = digits.getValue();
                    if (!SS7DHeadEntity.validDigits(value)) return;
                    HexieNetworkHandler.sendToServer(new SS7DNumberUpdatePacket(menu.containerId, value,
                            bureau.getValue(), depot.getValue(), !hideDepot.selected(), secondEnd.selected()));
                    onClose();
                }).bounds(leftPos + 12, topPos + 144, 76, 20).build());
        confirm.active = SS7DHeadEntity.validDigits(text);
        digits.setResponder(value -> confirm.active = SS7DHeadEntity.validDigits(value));
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(leftPos + 96, topPos + 144, 76, 20).build());
        setInitialFocus(digits);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF252525);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF727272);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 10, 0xFFFFFF, false);
        graphics.drawString(font, "SS7D", 22, 34, 0xFFFFFF, false);
        graphics.drawString(font, "局", 78, 70, 0xFFFFFF, false);
        graphics.drawString(font, "段", 164, 70, 0xFFFFFF, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
