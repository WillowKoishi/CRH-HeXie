package willow.train.hexie.client.screens.controls;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.client.screens.HexieInventory;
import willow.train.hexie.client.screens.controls.widget.ABCTagBox;
import willow.train.hexie.client.screens.controls.widget.ACCTagBox;
import willow.train.hexie.client.screens.controls.widget.ItemSlotButton;
import willow.train.hexie.client.screens.widget.DoubleImageButton;
import willow.train.hexie.client.util.GuiImageCache;
import willow.train.hexie.client.util.RenderableItem;
import willow.train.hexie.client.util.RenderablePicture;
import willow.train.hexie.compat.ItemData;
import willow.train.hexie.init.item.ResourceItemInit;
import willow.train.hexie.item.AnimatableBlockControllerItem;
import willow.train.hexie.menu.AnimatableBlockControllerMenu;

public class AnimatableBlockControllerScreen extends AbstractContainerScreen<AnimatableBlockControllerMenu> {

    public static final Component TITLE = Component.translatable("container.hexie.abc.title");
    private final HexieInventory ivt = new HexieInventory();
    private final Inventory playerInventory;
    private final int controllerSlot;
    private final ItemStack holding;
    private final DoubleImageButton goBtn;
    private final RenderableItem iconItem;
    private final float zoom = 1.5f;
    public final RenderablePicture background, goBtnUp, goBtnDown;
    private final CompoundTag tag;
    private ABCTagBox tagBox;
    private int x, y;
    private boolean saved;
    private boolean inventoryFull;

    public AnimatableBlockControllerScreen(AnimatableBlockControllerMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.playerInventory = pPlayerInventory;
        this.controllerSlot = pMenu.getControllerSlot();
        this.holding = pMenu.getControllerStack();
        this.tag = ItemData.getTag(holding);
        GuiImageCache.ImageData bg = GuiImageCache.register("textures/gui/controller/controller.png", 0);
        this.background = new RenderablePicture(bg, 128, 81);
        this.goBtnDown = new RenderablePicture(bg, 0, 81, 17, 11);
        this.goBtnUp = new RenderablePicture(bg, 0, 92, 17, 11);
        this.background.zoom(zoom);
        ivt.inventoryMode();
        ivt.setParentScreen(this);
        ivt.setSlotLock(controllerSlot);
        goBtn = new DoubleImageButton(goBtnUp, goBtnDown, pButton -> onClose());
        goBtn.setZoom(zoom);
        iconItem = new RenderableItem(holding, 27, 27);
    }

    @Override
    protected void init() {
        super.init();
        // Keep the edited selection and coupling when the window is resized.
        if (tagBox == null) {
            tagBox = new ABCTagBox(tag, this, minecraft.getItemRenderer(), b -> {
                ItemStack hovering = ivt.getHoveringItem();
                if (hovering.is(ResourceItemInit.AutomaticControlCoupling.get())
                        && ItemData.getOrCreateTagElement(hovering, "contents").isEmpty())
                    return;
                ivt.setHoveringItem(((ItemSlotButton) b).putStackIn(hovering));
                AnimatableBlockControllerItem.loadCoupling(holding, ((ItemSlotButton) b).getStackIn());
            }, zoom);
        }
        ivt.y = this.height - ivt.height() - 30;
        ivt.horizontalCenter(this);
        this.x = (this.width - background.width()) / 2;
        this.y = ivt.y - background.height() - 3;
        this.goBtn.setX(this.x + (int) (104 * zoom));
        this.goBtn.setY(this.y + (int) (66 * zoom));
        tagBox.setPosition(x + (int) (9 * zoom), y + (int) (24 * zoom));
    }

    @Override
    public void render(GuiGraphics pose, int mouseX, int mouseY, float partial) {
        background.renderLazy(pose, x, y);
        ivt.render(pose, minecraft.getItemRenderer(), playerInventory.player, mouseX, mouseY);
        ACCTagBox.renderTextCentered(pose, TITLE.copy().withStyle(ChatFormatting.BOLD), x + 64 * zoom, y + 7.5f * zoom);
        iconItem.renderCentered(pose, minecraft.getItemRenderer(), x + (int) (10 * zoom), y + (int) (70 * zoom));
        goBtn.render(pose, mouseX, mouseY, partial);
        tagBox.render(pose, mouseX, mouseY, partial);
        if (inventoryFull && !ivt.getHoveringItem().isEmpty())
            pose.drawCenteredString(font, Component.translatable("container.hexie.abc.inventory_full"),
                    width / 2, ivt.y + ivt.height() + 4, 0xff5555);
    }

    @Override
    public void onClose() {
        if (!saved && tagBox != null && holding.getItem() instanceof AnimatableBlockControllerItem
                && menu.stillValid(playerInventory.player)) {
            AnimatableBlockControllerItem.setNumber(holding, tagBox.getNumber());
            AnimatableBlockControllerItem.loadCoupling(holding, tagBox.getSlot());
            playerInventory.setItem(controllerSlot, holding);
            ivt.markDirty(controllerSlot);
            if (!ivt.onClose(playerInventory)) {
                inventoryFull = true;
                return;
            }
            saved = true;
        }
        super.onClose();
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (goBtn.mouseClicked(pMouseX, pMouseY, pButton)) return true;
        if (tagBox.mouseClicked(pMouseX, pMouseY, pButton)) return true;
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {}

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (goBtn.mouseReleased(pMouseX, pMouseY, pButton)) return true;
        if (tagBox.mouseReleased(pMouseX, pMouseY, pButton)) return true;
        ivt.handleMouseClick(playerInventory, playerInventory.player.isCreative(),
                (int) pMouseX, (int) pMouseY, pButton);
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDeltaX, double pDelta) {
        if (tagBox.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta)) return true;
        return super.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
