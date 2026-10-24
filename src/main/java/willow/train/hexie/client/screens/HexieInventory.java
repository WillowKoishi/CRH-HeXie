package willow.train.hexie.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import willow.train.hexie.client.util.GuiImageCache;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.InventoryUpdatePacket;
import willow.train.hexie.client.util.RenderablePicture;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class HexieInventory {
    public static final GuiImageCache.ImageData INVENTORY_IMAGE = GuiImageCache.register("textures/gui/inventory.png", 0);
    private RenderablePicture inventoryBackground, quickBarBackground, chosenFrame;
    private final int basic_x = 8;
    boolean renderQuickBar = false;

    public int x, y;
    public List<Integer> slotLocks = new ArrayList<>();
    public ArrayList<Integer> dirtySlots = new ArrayList<>();
    private Screen parentScreen;
    private ItemStack hoveringItem;

    public HexieInventory() {
        this.inventoryBackground = new RenderablePicture(INVENTORY_IMAGE, 175, 90);
        this.quickBarBackground = inventoryBackground.cut(0, 62, 0, 0);
        this.chosenFrame = new RenderablePicture(INVENTORY_IMAGE, 192, 0, 18, 18);
    }

    public void setParentScreen(Screen screen) {
        this.parentScreen = screen;
    }

    public Screen getParentScreen() {
        return parentScreen;
    }

    public void quickBarMode() {
        this.renderQuickBar = true;
    }

    public void horizontalCenter(Screen screen) {
        if(screen == null) return;
        this.x = (screen.width - inventoryBackground.width())/2;
    }

    public int height() {
        return isRenderingQuickBar() ? quickBarBackground.height() : inventoryBackground.height();
    }

    public void inventoryMode() {
        this.renderQuickBar = false;
    }

    public boolean isRenderingQuickBar() {
        return renderQuickBar;
    }

    public ItemStack getHoveringItem() {
        if(hoveringItem == null) return Items.AIR.getDefaultInstance();
        return hoveringItem;
    }

    public ItemStack setHoveringItem(ItemStack stackIn) {
        if(stackIn.is(Items.AIR)){
            ItemStack cache = hoveringItem;
            hoveringItem = Items.AIR.getDefaultInstance();
            return cache;
        } else {
            ItemStack cache = hoveringItem;
            hoveringItem = stackIn;
            return cache;
        }
    }

    public void render(GuiGraphics stack,ItemRenderer renderer, Player player, int mouseX, int mouseY){
        if(isRenderingQuickBar())
            renderQuickBar(stack, renderer, player, x, y, mouseX, mouseY);
        else
            renderInventory(stack, renderer, player, x, y, mouseX, mouseY);
    }

    public void renderInventory(GuiGraphics stack, ItemRenderer renderer, Player player, int x, int y, int mouseX, int mouseY) {
        if(player == null) return;
        Inventory inventory = player.getInventory();
        Font font = Minecraft.getInstance().font;
        inventoryBackground.renderLazy(x, y);
        for(int i = 0; i < 27; i++) {
            int px = x + basic_x + (i % 9) * 18;
            int py = y + basic_x + 1 + (i / 9) * 18;
            ItemStack iStack = inventory.getItem(i + 9);
            stack.renderItem(iStack, px, py);
            stack.renderItemDecorations(font, iStack, px, py);
        }
        renderQuickBar(stack, renderer, player, x, y + 59, mouseX, mouseY);
        renderHoveringItem(stack, renderer, font, mouseX, mouseY);
        renderTooltip(stack, inventory, mouseX, mouseY);
    }



    public void renderQuickBar(GuiGraphics stack, ItemRenderer renderer, Player player, int x, int y, int mouseX, int mouseY) {
        if(player == null) return;
        Inventory inventory = player.getInventory();
        Font font = Minecraft.getInstance().font;
        if(isRenderingQuickBar())
            quickBarBackground.renderLazy(x, y);
        for(int i = 0; i < 9; i++) {
            int px = x + basic_x + (i % 9) * 18;
            int py = y + basic_x - (isRenderingQuickBar() ? 3 : 0);
            ItemStack iStack = inventory.getItem(i);
            stack.renderItem(iStack, px, py);
            stack.renderItemDecorations(font, iStack, px, py);
        }
        if(isRenderingQuickBar()) {
            renderHoveringItem(stack, renderer, font, mouseX, mouseY);
            renderTooltip(stack, inventory, mouseX, mouseY);
        }
    }

    public void renderHoveringItem(GuiGraphics stack, ItemRenderer renderer, Font font, int mouseX, int mouseY) {
        if(hoveringItem == null) return;
        if(hoveringItem.is(Items.AIR)) return;
        stack.renderItem(hoveringItem, mouseX - 8, mouseY - 8);
        stack.renderItemDecorations(font, hoveringItem, mouseX - 8, mouseY - 8);
    }

    protected void renderTooltip(GuiGraphics stack, Inventory inventory, int mouseX, int mouseY) {
        if(parentScreen == null) return;
        int i = getIndexFromPos(mouseX, mouseY);
        if(i < 0 || i > 35) return;
        ItemStack chosen = inventory.getItem(i);
        if(isRenderingQuickBar())
            chosenFrame.renderLazy(x + basic_x + (i % 9) * 18 - 1, y - 4 + basic_x);
        else{
            if(i < 9)
                chosenFrame.renderLazy(x + basic_x + (i % 9) * 18 - 1, y + 58 + basic_x);
            else
                chosenFrame.renderLazy(x + basic_x + (i % 9) * 18 - 1, y + basic_x + ((i-9) / 9) * 18);
        }
        if (Minecraft.getInstance().screen != null && !chosen.is(Items.AIR)) {
            stack.renderTooltip(Minecraft.getInstance().font, Screen.getTooltipFromItem(Minecraft.getInstance(), chosen),
                    chosen.getTooltipImage(), mouseX + 5, mouseY + 10);
        }
    }

    private int getIndexFromPos(int px, int py) {
        if(px < x || py < y) return -1;
        if(px > x + inventoryBackground.width()) return -1;
        if(isRenderingQuickBar() && py > y + quickBarBackground.height()) return -1;
        if(!isRenderingQuickBar() && py > y + inventoryBackground.height()) return -1;

        int colum = -1;
        int c = (px - basic_x - x) / 18;
        int pc = c * 18 + basic_x + x;
        if(px > pc && px < pc + 16) {
            colum = c;
            int r = (py - basic_x - 1 - y)/18;
            int pr = r * 18 + 1 + basic_x + y;
            if(r < 3 && py > pr && py < pr + 16) {
                if(isRenderingQuickBar() && r == 0)
                    return colum;
                return (r+1) * 9 + colum;
            } else if (!isRenderingQuickBar() && py > y + basic_x + 59 && py < y + basic_x + 75){
                return colum;
            }
        }
        return -1;
    }

    public void markDirty(Integer slot) {
        this.dirtySlots.add((Integer) slot);
    }

    public void handleMouseClick(Inventory inventory, boolean isCreative, int mouseX, int mouseY, int button) {
        // 0 -> left
        // 1 -> right
        // 2 -> middle
        if(mouseX < x || mouseY < y) return;
        if(mouseX > x + inventoryBackground.width()) return;
        if(isRenderingQuickBar() && mouseY > y + quickBarBackground.height()) return;
        if(!isRenderingQuickBar() && mouseY > y + inventoryBackground.height()) return;

        int index = getIndexFromPos(mouseX, mouseY);
        if(index < 0 || isLocked(index)) return;
        ItemStack item = inventory.getItem(index);
        if(item.is(Items.AIR)) {
            if(hoveringItem == null) return;
            if(hoveringItem.is(Items.AIR)) return;
        }
        markDirty(index);
        if(!item.is(Items.AIR)) {
            if(hoveringItem != null) {
                if(ItemStack.isSameItemSameComponents(item, hoveringItem) &&
                        item.isStackable() &&
                        hoveringItem.isStackable() &&
                        item.getCount() < item.getMaxStackSize()) {
                    if(button == 0) {
                        int dCount = item.getMaxStackSize() - item.getCount();
                        if (dCount >= hoveringItem.getCount()) {
                            item.setCount(item.getCount() + hoveringItem.getCount());
                            hoveringItem = Items.AIR.getDefaultInstance();
                        } else {
                            item.setCount(item.getMaxStackSize());
                            hoveringItem.setCount(hoveringItem.getCount() - dCount);
                        }
                    } else if (button == 1) {
                        if(item.getCount() < item.getMaxStackSize())
                            item.setCount(item.getCount() + 1);
                        if(hoveringItem.getCount() > 1)
                            hoveringItem.setCount(hoveringItem.getCount() - 1);
                        else
                            hoveringItem = Items.AIR.getDefaultInstance();
                    }
                } else {
                    inventory.setItem(index, hoveringItem);
                    hoveringItem = item;
                }
            } else {
                if(button == 2 && isCreative) {
                    hoveringItem = item.copyWithCount(item.getMaxStackSize());
                } else if(button == 1 && item.isStackable() && item.getCount() > 1) {
                    hoveringItem = item.copyWithCount(1);
                    item.setCount(item.getCount() - 1);
                } else {
                    hoveringItem = item;
                    inventory.setItem(index, Items.AIR.getDefaultInstance());
                }
            }
        } else if (hoveringItem != null) {
            if(button == 1 && hoveringItem.isStackable() && hoveringItem.getCount() > 1) {
                ItemStack newStack = hoveringItem.copyWithCount(1);
                inventory.setItem(index, newStack);
                hoveringItem.setCount(hoveringItem.getCount() - 1);
            } else {
                inventory.setItem(index, hoveringItem);
                hoveringItem = Items.AIR.getDefaultInstance();
            }
        }

    }

    public void setSlotLock(int slotIndex) {
        if(slotIndex < 0) return;
        if(isRenderingQuickBar() && slotIndex > 8) return;
        if(!isRenderingQuickBar() && slotIndex > 35) return;
        slotLocks.add(slotIndex);
    }

    protected void sendUpdateMessage() {
        if(Minecraft.getInstance().player == null) return;
        ArrayList<ItemStack> dirtyStacks = new ArrayList<>();
        for(Integer index : dirtySlots) {
            dirtyStacks.add(Minecraft.getInstance().player.getInventory().getItem(index));
        }
        HexieNetworkHandler.sendToServer(new InventoryUpdatePacket(dirtySlots, dirtyStacks));
        dirtySlots.clear();
    }

    public void removeSlotLock(int slotIndex) {
        slotLocks.remove((Integer) slotIndex);
    }

    public boolean isLocked(int slotIndex) {
        return slotLocks.contains((Integer) slotIndex);
    }

    public List<Integer> getLocks() {
        return slotLocks;
    }

    public void removeAllLocks() {
        slotLocks.clear();
    }

    public void onClose(Inventory inventory) {
        if(hoveringItem != null) {
            inventory.add(hoveringItem);
            hoveringItem = Items.AIR.getDefaultInstance();
        }
        sendUpdateMessage();
        removeAllLocks();
    }

}
