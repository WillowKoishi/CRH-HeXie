package willow.train.hexie.client.screens.controls;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.menu.AnimatableBlockControllerMenu;
import willow.train.hexie.client.screens.controls.widget.ABCTagBox;
import willow.train.hexie.client.screens.controls.widget.ACCTagBox;
import willow.train.hexie.client.screens.controls.widget.ItemSlotButton;
import willow.train.hexie.client.screens.widget.DoubleImageButton;
import willow.train.hexie.client.util.GuiImageCache;
import willow.train.hexie.init.client.MenuInit;
import willow.train.hexie.client.screens.HexieInventory;
import willow.train.hexie.init.item.ResourceItemInit;
import willow.train.hexie.item.AnimatableBlockControllerItem;
import willow.train.hexie.client.util.RenderableItem;
import willow.train.hexie.client.util.RenderablePicture;

public class AnimatableBlockControllerScreen extends AbstractContainerScreen<AnimatableBlockControllerMenu> {

    HexieInventory ivt = MenuInit.INVENTORY_WIDGET;

    public static final Component TITLE = Component.translatable("container.hexie.abc.title");
    private ItemStack holding;
    int x, y;
    private DoubleImageButton goBtn;
    private RenderableItem iconItem;
    private ABCTagBox tagBox;
    private final float zoom = 1.5f;
    public final RenderablePicture background, goBtnUp, goBtnDown;
    CompoundTag tag;

    public AnimatableBlockControllerScreen(AnimatableBlockControllerMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        GuiImageCache.ImageData bg = GuiImageCache.register("textures/gui/controller/controller.png", 0);
        this.background = new RenderablePicture(bg, 128, 81);
        this.goBtnDown = new RenderablePicture(bg, 0, 81, 17, 11);
        this.goBtnUp = new RenderablePicture(bg, 0, 92, 17, 11);
        this.background.zoom(zoom);
        ivt.inventoryMode();
        ivt.setParentScreen(this);
        if(Minecraft.getInstance().player != null) {
            Inventory inventory = Minecraft.getInstance().player.getInventory();
            this.holding = inventory.getItem(inventory.selected);
            ivt.setSlotLock(inventory.selected);
        }
        if(holding.getItem() instanceof AnimatableBlockControllerItem) {
            tag = willow.train.hexie.compat.ItemData.getTag(holding);
        }
        goBtn = new DoubleImageButton(goBtnUp, goBtnDown, pButton -> onClose());
        goBtn.setZoom(zoom);
        iconItem = new RenderableItem(holding, 27, 27);
    }

    @Override
    protected void init() {
        super.init();
        if (Minecraft.getInstance().screen == null) return;
        tagBox = new ABCTagBox(tag, this, Minecraft.getInstance().getItemRenderer(), b -> {
            if(ivt.getHoveringItem().is(ResourceItemInit.AutomaticControlCoupling.get())) {
                if(!willow.train.hexie.compat.ItemData.hasTag(ivt.getHoveringItem())) return;
                if(willow.train.hexie.compat.ItemData.getOrCreateTagElement(ivt.getHoveringItem(), "contents").isEmpty()) return;
            }
            ivt.setHoveringItem(((ItemSlotButton) b).putStackIn(ivt.getHoveringItem()));
            AnimatableBlockControllerItem.loadCoupling(holding, ((ItemSlotButton)b).getStackIn());
        }, zoom);
        int width = Minecraft.getInstance().screen.width;
        ivt.y = this.height - ivt.height() - 30;
        ivt.horizontalCenter(this);
        this.x = (width - (int) (128 * zoom)) / 2;
        this.y = ivt.y - background.height() - 3;
        this.goBtn.setX(this.x + (int)(104 * zoom));
        this.goBtn.setY(this.y + (int)(66 * zoom));
        tagBox.setPosition(x + (int)(9 * zoom), y + (int)(24 * zoom));
    }

    @Override
    public void render(GuiGraphics pose, int mouseX, int mouseY, float partial) {
        background.renderLazy(x, y);
        ivt.render(pose, Minecraft.getInstance().getItemRenderer(), minecraft.player, mouseX, mouseY);
        ACCTagBox.renderTextCentered(pose, TITLE.copy().withStyle(ChatFormatting.BOLD), x + 64 * zoom, y + 7.5f * zoom);
        if (holding != null)
            iconItem.renderCentered(pose, Minecraft.getInstance().getItemRenderer(), x + (int) (10 * zoom), y + (int) (70 * zoom));
        goBtn.render(pose, mouseX, mouseY, partial);
        tagBox.render(pose, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        super.onClose();
        ItemStack inner = tagBox.getSlot();
        int number = tagBox.getNumber();
        Inventory inventory = getMinecraft().player.getInventory();
        AnimatableBlockControllerItem.setNumber(holding, number);
        AnimatableBlockControllerItem.loadCoupling(holding, inner);
        ivt.markDirty(inventory.selected);
        ivt.onClose(inventory);
        inventory.setItem(inventory.selected, holding);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if(goBtn.mouseClicked(pMouseX, pMouseY, pButton)) return true;
        if(tagBox.mouseClicked(pMouseX, pMouseY, pButton)) return true;
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {}

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if(goBtn.mouseReleased(pMouseX, pMouseY, pButton)) return true;
        if(tagBox.mouseReleased(pMouseX, pMouseY, pButton)) return true;
        if (Minecraft.getInstance().player != null) {
            ivt.handleMouseClick(Minecraft.getInstance().player.getInventory(), Minecraft.getInstance().player.isCreative(),
                    (int) pMouseX,(int) pMouseY, pButton);
        }
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDeltaX, double pDelta) {
        if(tagBox.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta)) return true;
        return super.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
