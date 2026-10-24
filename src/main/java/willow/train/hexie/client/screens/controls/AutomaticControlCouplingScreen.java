package willow.train.hexie.client.screens.controls;

import com.google.common.collect.Lists;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.menu.AutomaticControlCouplingMenu;
import willow.train.hexie.client.screens.HexieInventory;
import willow.train.hexie.client.screens.controls.widget.ACCTagBox;
import willow.train.hexie.client.screens.controls.widget.ScrollBar;
import willow.train.hexie.client.screens.widget.DoubleImageButton;
import willow.train.hexie.client.util.GuiImageCache;
import willow.train.hexie.init.item.ResourceItemInit;
import willow.train.hexie.item.AutomaticControlCouplingItem;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.InventoryUpdatePacket;
import willow.train.hexie.client.util.RenderableItem;
import willow.train.hexie.client.util.RenderablePicture;

import java.util.List;

public class AutomaticControlCouplingScreen extends AbstractContainerScreen<AutomaticControlCouplingMenu> {

    public static final GuiImageCache.ImageData CONTROLLER_IMAGE = GuiImageCache.register("textures/gui/controller/controller.png", 0);
    public static final Component TITLE = Component.translatable("container.hexie.acc.title");
    public final RenderablePicture background, goBtnUp, goBtnDown, emptyBg;
    ScrollBar scrollBar;
    ItemStack holding;
    int x, y;
    private final float zoom = 2f;
    ItemRenderer renderer;
    private RenderableItem iconItem;
    private DoubleImageButton goBtn;
    List<ACCTagBox> tagList = Lists.newArrayList();

    public AutomaticControlCouplingScreen(AutomaticControlCouplingMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.background = new RenderablePicture(CONTROLLER_IMAGE, 128, 81);
        this.goBtnDown = new RenderablePicture(CONTROLLER_IMAGE, 0, 81, 17, 11);
        this.goBtnUp = new RenderablePicture(CONTROLLER_IMAGE, 0, 92, 17, 11);
        this.emptyBg = new RenderablePicture(HexieInventory.INVENTORY_IMAGE, 0, 91, 110, 14);
        emptyBg.zoom(zoom);
        this.background.zoom(zoom);
        if(Minecraft.getInstance().player != null) {
            Inventory inventory = Minecraft.getInstance().player.getInventory();
            this.holding = inventory.getItem(inventory.selected);
        }
        if(holding.is(ResourceItemInit.AutomaticControlCoupling.get())) {
            List<AutomaticControlCouplingItem.CouplingDisplayData> datas = AutomaticControlCouplingItem.getDisplayDatas(holding);
            int counter = 0;
            for(AutomaticControlCouplingItem.CouplingDisplayData d : datas) {
                tagList.add(new ACCTagBox(d, zoom, counter));
                counter++;
            }
        }
        goBtn = new DoubleImageButton(goBtnUp, goBtnDown, pButton -> onCloseSuccess());
        goBtn.setZoom(zoom);
        scrollBar = new ScrollBar(0, 0, (int)(6 * zoom), (int)(43 * zoom), tagList.size(), true);
        scrollBar.setElementCount(2);
        iconItem = new RenderableItem(holding, 36, 36);
    }

    @Override
    protected void init() {
        super.init();
        if (Minecraft.getInstance().screen == null) return;
        int width = Minecraft.getInstance().screen.width;
        int height = Minecraft.getInstance().screen.height;
        this.renderer = Minecraft.getInstance().getItemRenderer();
        this.x = (width - (int) (128 * zoom)) / 2;
        this.y = (height - (int) (81 * zoom)) / 2;
        this.goBtn.setX(this.x + (int)(104 * zoom));
        this.goBtn.setY(this.y + (int)(66 * zoom));
    }

    @Override
    public void render(GuiGraphics pose, int mouseX, int mouseY, float partial) {
        //super.render(pose, mouseX, mouseY, partial);
        background.renderLazy(pose, x, y);
        ACCTagBox.renderTextCentered(pose, TITLE.copy().withStyle(ChatFormatting.BOLD), x + 64 * zoom, y + 7.5f * zoom);
        if(!tagList.isEmpty()) {
            int previousSize = tagList.size();
            tagList.removeIf(ACCTagBox::shouldRemove);
            scrollBar.setExactLength(tagList.size());
            int firstVisible = scrollBar.getRencent();
            boolean reindex = tagList.size() != previousSize;
            for (int i = 0; i < tagList.size(); i++) {
                ACCTagBox box = tagList.get(i);
                if (reindex)
                    box.index = Integer.toString(i);
                box.visible = i >= firstVisible && i < firstVisible + 2;
            }

            if(tagList.size() > firstVisible) {
                ACCTagBox firstTag = tagList.get(firstVisible);
                firstTag.setPosition(x + (int)(5 * zoom), y + (int)(21 * zoom));
                firstTag.render(pose, mouseX, mouseY, partial);
            }

            if(tagList.size() > firstVisible + 1) {
                ACCTagBox secondTag = tagList.get(firstVisible + 1);
                secondTag.setPosition(x + (int)(5 * zoom),y + (int)(41 * zoom));
                secondTag.render(pose, mouseX, mouseY, partial);
            }

            scrollBar.setX(x + (int) (119 * zoom));
            scrollBar.setY(y + (int) (17 * zoom));
            scrollBar.render(pose, mouseX, mouseY, partial);
        } else {
            emptyBg.renderLazy(pose, x + 1 + (int)(8 * zoom), y + (int)(31 * zoom));
            ACCTagBox.renderTextCentered(pose, Component.translatable("container.hexie.acc.empty"), x + 64 * zoom, y + 38 * zoom);
        }
        if (this.renderer != null && holding != null)
            iconItem.renderCentered(pose, renderer, x + (int) (15 * zoom), y + (int) (72 * zoom));
        goBtn.render(pose, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        scrollBar.onClick(pMouseX, pMouseY);
        goBtn.mouseClicked(pMouseX, pMouseY, pButton);
        tagList.forEach(box -> {if(box.visible)box.mouseClicked(pMouseX, pMouseY, pButton);});
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if(goBtn.mouseReleased(pMouseX, pMouseY, pButton)) return true;
        scrollBar.onRelease(pMouseX, pMouseY);
        tagList.forEach(box -> {if(box.visible)box.mouseReleased(pMouseX, pMouseY, pButton);});
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDeltaX, double pDelta) {
        tagList.forEach(ACCTagBox::forceLoseFocus);
        scrollBar.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta);
        return super.mouseScrolled(pMouseX, pMouseY, pDeltaX, pDelta);
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        tagList.forEach(box -> {if(box.visible)box.keyPressed(pKeyCode, pScanCode, pModifiers);});
        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    public void onCloseSuccess() {
        if(holding.getItem() instanceof AutomaticControlCouplingItem) {
            AutomaticControlCouplingItem.removeAllBlock(holding);
            if(tagList.isEmpty()) {
                AutomaticControlCouplingItem.removeBasic(holding);
            } else {
                tagList.forEach(box -> AutomaticControlCouplingItem.addBlock(holding, box.getNewData()));
            }
        }
        if(Minecraft.getInstance().player != null) {
            Inventory inventory = Minecraft.getInstance().player.getInventory();
            inventory.setItem(inventory.selected, holding);
            HexieNetworkHandler.sendToServer(new InventoryUpdatePacket(inventory.selected, holding));
        }
        super.onClose();
    }

    @Override
    public boolean charTyped(char pCodePoint, int pModifiers) {
        tagList.forEach(box -> {if (box.visible) box.charTyped(pCodePoint, pModifiers);});
        return super.charTyped(pCodePoint, pModifiers);
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {}

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
