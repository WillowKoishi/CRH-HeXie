package willow.train.hexie.client.screens.controls.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import willow.train.hexie.init.item.ResourceItemInit;
import willow.train.hexie.interaction.InteractionProcessor;
import willow.train.hexie.interaction.base.ICustomInteractionGroup;
import willow.train.hexie.item.AnimatableBlockControllerItem;
import willow.train.hexie.client.util.RenderablePicture;

public class ABCTagBox extends AbstractWidget {
    public final RenderablePicture background;
    ScrollingComponentBox box;
    CompoundTag tag;
    ItemSlotButton slot;
    float zoom = 1.0f;

    public ABCTagBox(CompoundTag tag, Screen screen, ItemRenderer renderer, Button.OnPress onPress, float zoom) {
        super(0, 0, (int)(110 * zoom), (int)(28 * zoom), Component.empty());
        background = new RenderablePicture(ACCTagBox.CONTROLLER_IMAGE, 17, 95, 110, 28);
        this.tag = tag;
        this.zoom = zoom;
        background.zoom(zoom);
        this.box = new ScrollingComponentBox(Minecraft.getInstance().font, (int)(47 * zoom), (int)(8 * zoom));
        CompoundTag recentTag = tag.getCompound("recent");
        for(String groupKey : InteractionProcessor.INTERACTIONS.keySet()) {
            ICustomInteractionGroup group = InteractionProcessor.getGroup(groupKey);
            box.addTitle(group.descriptionId());
            for(String subKey : group.interactions().keySet()) {
                box.addComponent(group.getInteraction(subKey).descriptionKey());
            }
        }
        box.setCursorWithoutTitle(recentTag.getInt("number"));
        slot = new ItemSlotButton((int)(16 * zoom), (int)(16 * zoom), zoom, screen, renderer, onPress, ResourceItemInit.AutomaticControlCoupling.get());
        ItemStack inner = AnimatableBlockControllerItem.generateCoupling(tag);
        if(inner.is(Items.AIR)) return;
        slot.putStackIn(inner);
    }

    public void setPosition(int x, int y) {
        this.setX(x);
        this.setY(y);
        box.setX(this.getX() + (int)(25 * zoom));
        box.setY(this.getY() + (int)(11 * zoom));
        slot.setX(x + (int)(83 * zoom));
        slot.setY(y + (int)(7 * zoom));
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        background.renderLazy(pGuiGraphics, getX(), getY());
        slot.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        box.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDeltaX, double pDelta) {
        if(box.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta)) return true;
        return super.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if(slot.mouseClicked(pMouseX, pMouseY, pButton)) return true;
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        return slot.mouseReleased(pMouseX, pMouseY, pButton);
    }

    public ItemStack getSlot() {
        return slot.getStackIn();
    }

    public int getNumber() {
        return box.getCursorWithoutTitle();
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {}
}
