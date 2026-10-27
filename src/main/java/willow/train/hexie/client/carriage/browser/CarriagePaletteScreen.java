package willow.train.hexie.client.carriage.browser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.mixins.mixin.client.CreativeModeInventoryScreenAccessor;
import java.util.List;

/** A compact anchored palette using vanilla creative click handling and inventory packets. */
public final class CarriagePaletteScreen extends CreativeModeInventoryScreen {
    private final CarriageBrowserView parent;
    private final List<ItemStack> variants;
    private static final int PAGE_SIZE = 15;
    private final int anchorX, anchorY;
    private int columns, rows, displayedCount = -1;
    private List<Slot> originalSlots;
    private boolean returning;
    private int page;

    public CarriagePaletteScreen(CarriageBrowserView parent, List<ItemStack> variants, int x, int y) {
        super(Minecraft.getInstance().player, Minecraft.getInstance().player.connection.enabledFeatures(), false);
        this.parent = parent;
        this.variants = List.copyOf(variants);
        anchorX = x;
        anchorY = y;
        columns = Math.min(5, variants.size());
        rows = Math.min(3, (variants.size() + columns - 1) / columns);
        imageWidth = columns * 18 + 8;
        imageHeight = rows * 18 + 8;
    }

    @Override protected void init() {
        ItemStack cursor = menu.getCarried();
        super.init();
        ((CreativeModeInventoryScreenAccessor) (Object) this).hexie$selectTab(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(parent.tab));
        var pages = ((CreativeModeInventoryScreenAccessor) (Object) this).hexie$getPages();
        if (pages.size() > 1) pages.subList(1, pages.size()).clear();
        clearWidgets();
        if (originalSlots == null) originalSlots = List.copyOf(menu.slots);
        displayedCount = -1;
        refreshPage();
        menu.setCarried(cursor);
    }

    private void layoutPopup(int count) {
        displayedCount = count;
        columns = Math.min(5, count);
        rows = (count + columns - 1) / columns;
        imageWidth = columns * 18 + 8;
        imageHeight = rows * 18 + 8;
        // Clamp the complete panel to the preview's inner bounds, not the screen edges.
        int minX = parent.host.getGuiLeft() + 9, maxX = parent.host.getGuiLeft() + 169;
        int minY = parent.host.getGuiTop() + 37, maxY = parent.host.getGuiTop() + 106;
        leftPos = Math.max(minX, Math.min(maxX - imageWidth, parent.host.getGuiLeft() + anchorX + 6));
        topPos = Math.max(minY, Math.min(maxY - imageHeight, parent.host.getGuiTop() + anchorY + 6));
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot original = originalSlots.get(i);
            boolean visible = i >= 45 || i < displayedCount;
            int x = i < 45 ? 5 + (i % columns) * 18 : parent.host.getGuiLeft() - leftPos + 9 + (i - 45) * 18;
            int y = i < 45 ? 5 + (i / columns) * 18 : parent.host.getGuiTop() - topPos + 112;
            Slot slot = new Slot(original.container, original.getContainerSlot(), x, y) {
                @Override public boolean isActive() { return visible; }
                @Override public boolean mayPlace(ItemStack stack) { return original.mayPlace(stack); }
                @Override public boolean mayPickup(net.minecraft.world.entity.player.Player player) { return original.mayPickup(player); }
            };
            slot.index = i;
            menu.slots.set(i, slot);
        }
    }

    private void refreshPage() {
        page = Math.max(0, Math.min(page, (variants.size() - 1) / PAGE_SIZE));
        int count = Math.min(PAGE_SIZE, variants.size() - page * PAGE_SIZE);
        if (displayedCount != count) layoutPopup(count);
        menu.items.clear();
        for (int i = page * PAGE_SIZE; i < page * PAGE_SIZE + count; i++)
            menu.items.add(variants.get(i).copy());
        menu.scrollTo(0);
    }

    @Override public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        ItemStack cursor = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        try { parent.host.render(gui, -1, -1, partial); }
        finally { menu.setCarried(cursor); }
        refreshPage();
        gui.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        renderBg(gui, partial, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics gui, float partial, int x, int y) {
        // Tooltip-style black panel with a blue bevel; no empty item-slot decorations.
        int right = leftPos + imageWidth, bottom = topPos + imageHeight;
        gui.fill(leftPos, topPos, right, bottom, 0xf0101018);
        gui.hLine(leftPos, right - 1, topPos, 0xff5050ff);
        gui.hLine(leftPos, right - 1, bottom - 1, 0xff28287f);
        gui.fillGradient(leftPos, topPos + 1, leftPos + 1, bottom - 1, 0xff5050ff, 0xff28287f);
        gui.fillGradient(right - 1, topPos + 1, right, bottom - 1, 0xff5050ff, 0xff28287f);
        for (int i = 0; i < displayedCount; i++) {
            Slot slot = menu.slots.get(i);
            gui.blit(CarriageBrowserView.SLOT_TEXTURE, leftPos + slot.x - 1, topPos + slot.y - 1, 8, 17, 18, 18);
        }
    }

    @Override protected void renderLabels(GuiGraphics gui, int x, int y) {}
    @Override protected boolean checkTabClicked(CreativeModeTab tab, double x, double y) { return false; }
    @Override protected boolean checkTabHovering(GuiGraphics gui, CreativeModeTab tab, int x, int y) { return false; }
    @Override protected boolean insideScrollbar(double x, double y) { return false; }
    @Override public boolean charTyped(char character, int modifiers) { return false; }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inside(x, y)) { page += vertical > 0 ? -1 : vertical < 0 ? 1 : 0; refreshPage(); }
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (minecraft.options.keyChat.matches(key, scan)) return true;
        return super.keyPressed(key, scan, modifiers);
    }

    private boolean inside(double x, double y) {
        return x >= leftPos && x < leftPos + imageWidth && y >= topPos && y < topPos + imageHeight;
    }
    private boolean hotbar(double x, double y) {
        return x >= parent.host.getGuiLeft() + 8 && x < parent.host.getGuiLeft() + 170
            && y >= parent.host.getGuiTop() + 111 && y < parent.host.getGuiTop() + 129;
    }
    @Override protected boolean hasClickedOutside(double x, double y, int left, int top, int button) {
        return !inside(x, y) && !hotbar(x, y);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!inside(x, y) && !hotbar(x, y)) {
            double localX = x - parent.host.getGuiLeft(), localY = y - parent.host.getGuiTop();
            if (localX >= 9 && localX < 169 && localY >= 18 && localY < 106) menu.setCarried(ItemStack.EMPTY);
            onClose();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public void onClose() {
        ItemStack cursor = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        returning = true;
        parent.openingPalette = false;
        minecraft.setScreen(parent.host);
        parent.host.getMenu().setCarried(cursor);
    }
    @Override public void removed() {
        super.removed();
        if (!returning) CarriageBrowserClient.release(parent);
    }
}
