package willow.train.hexie.client.screens.controls.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import willow.train.hexie.client.util.RenderablePicture;

import java.util.Set;

import static willow.train.hexie.client.screens.HexieInventory.INVENTORY_IMAGE;

public class ItemSlotButton extends Button {

    private Set<Item> itemCouldIn;
    private boolean shouldLimitItem, isDown;
    private ItemStack stackIn = Items.AIR.getDefaultInstance();
    private RenderablePicture chosenFrame;
    private float itemSizeZoom = 1.0f;
    private final Screen screen;
    ItemRenderer renderer;

    public ItemSlotButton(int pWidth, int pHeight, float itemSizeZoom, Screen screen, ItemRenderer renderer, Button.OnPress onPress, Item... pItemLike) {
        this(pWidth, pHeight, itemSizeZoom, screen, renderer, onPress);
        itemCouldIn = Set.of(pItemLike);
        shouldLimitItem = true;
    }

    public ItemSlotButton(int pWidth, int pHeight, float itemSizeZoom, Screen screen, ItemRenderer renderer, Button.OnPress onPress) {
        super(0, 0, pWidth, pHeight, Component.empty(), onPress);
        itemCouldIn = Set.of();
        shouldLimitItem = false;
        this.itemSizeZoom = itemSizeZoom;
        this.screen = screen;
        this.renderer = renderer;
        chosenFrame = new RenderablePicture(INVENTORY_IMAGE, 192, 0, 18, 18);
    }

    public boolean couldIn(ItemStack stack) {
        if(!shouldLimitItem) return true;
        return itemCouldIn.contains(stack.getItem());
    }

    public ItemStack putStackIn(ItemStack stack) {
        if(stack.is(Items.AIR)) {
            ItemStack cache = stackIn != null && !stackIn.is(Items.AIR) ? stackIn : Items.AIR.getDefaultInstance();
            stackIn = Items.AIR.getDefaultInstance();
            return cache;
        }
        if(!couldIn(stack)) return stack;
        ItemStack stackCache = stackIn != null && !stackIn.is(Items.AIR) ? stackIn : Items.AIR.getDefaultInstance();
        stackIn = stack;
        return stackCache;
    }

    public ItemStack getStackIn() {
        return stackIn == null ? Items.AIR.getDefaultInstance() : stackIn;
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        pGuiGraphics.renderItem(stackIn, getX() + width/2 - 8, getY() + height/2 - 8);
        pGuiGraphics.renderItemDecorations(Minecraft.getInstance().font, stackIn, getX() + width/2 - 8, getY() + height/2 - 8);
        renderItemTooltip(pGuiGraphics, pMouseX, pMouseY);
    }
public void renderItemTooltip(GuiGraphics pose, int mouseX, int mouseY) {
        if(stackIn == null) return;
        if(!isMouseOver(mouseX, mouseY) || stackIn.is(Items.AIR)) return;
        chosenFrame.render(pose, getX() - 1, getY() - 1, width + 2, height + 2);
        pose.renderTooltip(Minecraft.getInstance().font, Screen.getTooltipFromItem(Minecraft.getInstance(), stackIn),
                stackIn.getTooltipImage(), mouseX + 5, mouseY + 10);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if(isMouseOver(pMouseX, pMouseY) && pButton == 0) {
            isDown = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if(isDown && isMouseOver(pMouseX, pMouseY) && pButton == 0) {
            isDown = false;
            onPress();
            ClientLevel level = Minecraft.getInstance().level;
            LocalPlayer player = Minecraft.getInstance().player;
            if(level != null && player != null){
                level.playLocalSound(player.getOnPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, .3f, 1f, false);
            }
            return true;
        }
        return false;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {}
}
