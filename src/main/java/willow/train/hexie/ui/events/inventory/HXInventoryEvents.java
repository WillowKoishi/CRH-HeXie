package willow.train.hexie.ui.events.inventory;

// split from willow/train/hexie/event/client/HXInventoryEvents.java

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.creative.BannerSection;
import willow.train.hexie.core.creative.SectionedCreativeTab;
import willow.train.hexie.ui.flow.CarriageFlowClient;
import willow.train.hexie.ui.flow.CarriageFlowPaletteScreen;
import willow.train.hexie.ui.screens.widget.GuiImageCache;
import willow.train.hexie.ui.screens.widget.ImageButton;
import willow.train.hexie.ui.screens.widget.ItemIconButton;
import willow.train.hexie.ui.screens.widget.RenderablePicture;

@OnlyIn(Dist.CLIENT)
public class HXInventoryEvents {
    private final CarriageInventoryFilter filter = new CarriageInventoryFilter();
    private final InventorySchematics schematics = new InventorySchematics();
    public static final GuiImageCache.ImageData UP_IMAGE = GuiImageCache.register("textures/gui/up_button.png", 0);
    public static final GuiImageCache.ImageData DOWN_IMAGE = GuiImageCache.register("textures/gui/down_button.png", 0);
    private static final float[] groupScroll = new float[8];
    private static CreativeModeTab currentCreativeTab = CreativeModeTabs.getDefaultTab();
    private static HXInventoryEvents instance;
    private static long showcaseSeed = ThreadLocalRandom.current().nextLong();
    private static WeakReference<CreativeModeInventoryScreen> lastShowcaseScreen = new WeakReference<>(null);
    @Nullable
    private static CreativeModeTab lastShowcaseTab;
    ItemIconButton[] imgBtn = new ItemIconButton[8];
    ImageButton[] upAndDownBtn = new ImageButton[2];
    int btn_location = 0;
    int showBtnNumber = 5;
    int guiLeft = 0, guiTop = 0;
    boolean onChanged = true;
    String playerName = "";

    public HXInventoryEvents() {
        instance = this;
    }


    public static void captureLegacyScroll(CreativeModeInventoryScreen screen) {
        if (instance == null || screen instanceof CarriageFlowPaletteScreen
                || CarriageFlowClient.previewMode()) return;
        if (isCarriageTab(CreativeModeInventoryScreen.selectedTab) && instance.filter.hasAppliedFilter(screen.getMenu()))
            groupScroll[CarriageInventoryFilter.carriageType] = screen.scrollOffs;
    }

    @Nullable
    private static SectionedCreativeTab activeSectionedTab(@Nullable CreativeModeInventoryScreen screen) {
        if (screen == null || screen instanceof CarriageFlowPaletteScreen || CarriageFlowClient.active(screen)) {
            return null;
        }
        CreativeModeTab selected = CreativeModeInventoryScreen.selectedTab;
        if (!(selected instanceof SectionedCreativeTab sectionedTab)
                || selected.getType() != CreativeModeTab.Type.CATEGORY
                || selected.hasSearchBar()) {
            return null;
        }
        return sectionedTab;
    }

    public static void applySectionedTabLayout(CreativeModeInventoryScreen screen, CreativeModeTab tab) {
        if (screen instanceof CarriageFlowPaletteScreen
                || !(tab instanceof SectionedCreativeTab sectionedTab)
                || tab.hasSearchBar()) {
            return;
        }
        if (lastShowcaseScreen.get() != screen || lastShowcaseTab != tab) {
            showcaseSeed = ThreadLocalRandom.current().nextLong();
            lastShowcaseScreen = new WeakReference<>(screen);
            lastShowcaseTab = tab;
        }
        CreativeModeInventoryScreen.ItemPickerMenu menu = screen.getMenu();
        menu.items.clear();
        menu.items.addAll(sectionedTab.getLayoutItems());
    }

    public static boolean isSectionBannerSlot(Screen screen, @Nullable Slot slot) {
        if (slot == null || !(screen instanceof CreativeModeInventoryScreen creative)) {
            return false;
        }
        SectionedCreativeTab tab = activeSectionedTab(creative);
        if (tab == null || !creative.isCreativeSlot(slot)) {
            return false;
        }
        int slotIdx = slot.getSlotIndex();
        if (slotIdx < 0 || slotIdx >= 45) {
            return false;
        }
        int visibleRow = slotIdx / SectionedCreativeTab.COLUMNS;
        int startRow = creative.getMenu().getRowIndexForScroll(creative.scrollOffs);
        return tab.isBannerRow(startRow + visibleRow);
    }

    public static void renderSectionBanners(CreativeModeInventoryScreen screen, GuiGraphics guiGraphics) {
        SectionedCreativeTab tab = activeSectionedTab(screen);
        if (tab == null) {
            return;
        }
        CreativeModeInventoryScreen.ItemPickerMenu menu = screen.getMenu();
        if (menu.slots.size() < 45) {
            return;
        }

        int startRow = menu.getRowIndexForScroll(screen.scrollOffs);
        int leftPos = screen.getGuiLeft();
        int topPos = screen.getGuiTop();
        Font font = Minecraft.getInstance().font;
        double elapsedSeconds = Util.getMillis() / 1000.0;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        for (int row = 0; row < 5; row++) {
            int absoluteRow = startRow + row;
            BannerSection banner = tab.getBannerAtRow(absoluteRow);
            if (banner == null) {
                continue;
            }
            Slot firstSlot = menu.slots.get(row * SectionedCreativeTab.COLUMNS);
            if (!screen.isCreativeSlot(firstSlot)) {
                continue;
            }

            int x = leftPos + firstSlot.x - 1;
            int y = topPos + firstSlot.y - 1;
            List<ItemStack> sectionItems = banner.hasItemShowcase() ? tab.getSectionItemsAtRow(absoluteRow) : List.of();
            boolean hasItems = banner.hasItemShowcase() && !sectionItems.isEmpty();

            if (banner.hasParallaxLayers() || hasItems) {
                renderParallaxBanner(guiGraphics, banner, absoluteRow, sectionItems, x, y, elapsedSeconds, screen.scrollOffs);
            } else {
                guiGraphics.blit(
                        banner.texture(),
                        x,
                        y,
                        0.0F,
                        0.0F,
                        BannerSection.BANNER_WIDTH,
                        BannerSection.BANNER_HEIGHT,
                        BannerSection.BANNER_WIDTH,
                        BannerSection.BANNER_HEIGHT
                );
            }

            // Render inner bevel + stationary title badge above scrolling 3D items (items sit around z=40).
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 80.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            if (banner.hasParallaxLayers() || hasItems) {
                int innerX = x + 1;
                int innerY = y + 1;
                int innerRight = innerX + BannerSection.INNER_WIDTH;
                int innerBottom = innerY + BannerSection.INNER_HEIGHT;
                guiGraphics.fill(innerX, innerY, innerRight, innerY + 1, 0x26FFFFFF);
                guiGraphics.fill(innerX, innerBottom - 1, innerRight, innerBottom, 0x36000000);
            }

            Component title = banner.title();
            int textWidth = font.width(title);
            int boxLeft = x + 2;
            int boxTop = y + 2;
            int boxBottom = y + BannerSection.BANNER_HEIGHT - 2;
            int maxBoxRight = x + BannerSection.BANNER_WIDTH - 2;
            int boxRight = Math.min(maxBoxRight, boxLeft + textWidth + 8);

            guiGraphics.fill(boxLeft, boxTop, boxRight, boxBottom, banner.overlayColor());

            int textX = boxLeft + 4;
            int textY = y + (BannerSection.BANNER_HEIGHT - font.lineHeight + 1) / 2;
            guiGraphics.enableScissor(boxLeft, boxTop, boxRight, boxBottom);
            guiGraphics.drawString(font, title, textX, textY, banner.textColor(), true);
            guiGraphics.disableScissor();
            guiGraphics.pose().popPose();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void renderParallaxBanner(
            GuiGraphics guiGraphics,
            BannerSection banner,
            int absoluteRow,
            List<ItemStack> sectionItems,
            int x,
            int y,
            double elapsedSeconds,
            float scrollOffs
    ) {
        int innerX = x + 1;
        int innerY = y + 1;
        int innerRight = innerX + BannerSection.INNER_WIDTH;
        int innerBottom = innerY + BannerSection.INNER_HEIGHT;

        // Draw fixed 1px light outer frame and flush before immediate parallax blits.
        guiGraphics.fill(x, y, x + BannerSection.BANNER_WIDTH, y + BannerSection.BANNER_HEIGHT, banner.borderColor());
        guiGraphics.flush();

        guiGraphics.enableScissor(innerX, innerY, innerRight, innerBottom);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (banner.hasParallaxLayers()) {
            for (BannerSection.ParallaxLayer layer : banner.layers()) {
                renderSeamlessParallaxLayer(guiGraphics, layer, innerX, innerY, elapsedSeconds, scrollOffs);
            }
        } else {
            guiGraphics.blit(
                    banner.texture(),
                    x,
                    y,
                    0.0F,
                    0.0F,
                    BannerSection.BANNER_WIDTH,
                    BannerSection.BANNER_HEIGHT,
                    BannerSection.BANNER_WIDTH,
                    BannerSection.BANNER_HEIGHT
            );
        }

        if (banner.hasItemShowcase() && !sectionItems.isEmpty()) {
            renderShowcaseItems(guiGraphics, banner.itemShowcase(), absoluteRow, sectionItems, innerX, innerY, elapsedSeconds, scrollOffs);
        }

        guiGraphics.disableScissor();
    }

    private static void renderShowcaseItems(
            GuiGraphics guiGraphics,
            BannerSection.ItemShowcase showcase,
            int absoluteRow,
            List<ItemStack> sectionItems,
            int viewportX,
            int viewportY,
            double elapsedSeconds,
            float scrollOffs
    ) {
        int count = sectionItems.size();
        int pitch = showcase.itemPitch();
        float scale = showcase.itemScale();
        float itemDrawSize = 16.0F * scale;
        float drawY = viewportY + (BannerSection.INNER_HEIGHT - itemDrawSize) * 0.5F;
        // Center rendered 3D items at z = 40 regardless of scale (renderItem adds +150 internally).
        float zOffset = 40.0F - 150.0F * scale;

        double scrollPx = elapsedSeconds * showcase.speedPxPerSec() + scrollOffs * showcase.scrollParallax();
        long firstSlot = (long) Math.floor((scrollPx - itemDrawSize) / pitch);
        long lastSlot = (long) Math.ceil((scrollPx + BannerSection.INNER_WIDTH) / pitch);
        long bannerSeed = mix64(showcaseSeed ^ ((long) absoluteRow * 0x9E3779B97F4A7C15L));

        long cachedCycle = Long.MIN_VALUE;
        int[] cachedPerm = null;

        for (long slotIdx = firstSlot; slotIdx <= lastSlot; slotIdx++) {
            float drawX = viewportX + (float) (slotIdx * (double) pitch - scrollPx);
            if (drawX + itemDrawSize <= viewportX || drawX >= viewportX + BannerSection.INNER_WIDTH) {
                continue;
            }

            int itemIndex;
            if (count == 1) {
                itemIndex = 0;
            } else if (!showcase.randomize()) {
                itemIndex = Math.floorMod(slotIdx, count);
            } else {
                long cycle = Math.floorDiv(slotIdx, count);
                int posInCycle = Math.floorMod(slotIdx, count);
                if (cycle != cachedCycle || cachedPerm == null) {
                    cachedPerm = shuffledCycle(bannerSeed, cycle, count);
                    cachedCycle = cycle;
                }
                itemIndex = cachedPerm[posInCycle];
            }

            ItemStack stack = sectionItems.get(itemIndex);
            if (stack.isEmpty()) {
                continue;
            }

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(drawX, drawY, zOffset);
            guiGraphics.pose().scale(scale, scale, scale);
            guiGraphics.renderFakeItem(stack, 0, 0);
            guiGraphics.pose().popPose();
        }
    }

    private static int[] shuffledCycle(long bannerSeed, long cycle, int count) {
        if (count == 2) {
            return (bannerSeed & 1L) == 0L ? new int[] {0, 1} : new int[] {1, 0};
        }
        int[] perm = rawShuffledCycle(bannerSeed, cycle, count);
        if (count >= 3) {
            int prevLast = rawShuffledCycle(bannerSeed, cycle - 1L, count)[count - 1];
            if (perm[0] == prevLast) {
                int tmp = perm[0];
                perm[0] = perm[1];
                perm[1] = tmp;
            }
        }
        return perm;
    }

    private static int[] rawShuffledCycle(long bannerSeed, long cycle, int count) {
        int[] perm = new int[count];
        for (int i = 0; i < count; i++) {
            perm[i] = i;
        }
        long state = mix64(bannerSeed ^ (cycle * 0xBF58476D1CE4E5B9L));
        for (int i = count - 1; i > 0; i--) {
            state = mix64(state + 0x9E3779B97F4A7C15L);
            int j = (int) Long.remainderUnsigned(state, i + 1);
            int tmp = perm[i];
            perm[i] = perm[j];
            perm[j] = tmp;
        }
        return perm;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static void renderSeamlessParallaxLayer(
            GuiGraphics guiGraphics,
            BannerSection.ParallaxLayer layer,
            int viewportX,
            int viewportY,
            double elapsedSeconds,
            float scrollOffs
    ) {
        int tw = layer.textureWidth();
        int th = layer.textureHeight();
        double rawOffset = elapsedSeconds * layer.speedPxPerSec() + scrollOffs * layer.scrollParallax();
        double wrapped = rawOffset % tw;
        if (wrapped < 0.0) {
            wrapped += tw;
        }
        if (wrapped >= tw) {
            wrapped = 0.0;
        }
        int intOffset = (int) wrapped;
        float frac = (float) (wrapped - intOffset);

        guiGraphics.pose().pushPose();
        if (frac != 0.0F) {
            guiGraphics.pose().translate(-frac, 0.0F, 0.0F);
        }

        int targetWidth = frac == 0.0F ? BannerSection.INNER_WIDTH : BannerSection.INNER_WIDTH + 1;
        int drawn = 0;
        int u = intOffset;
        while (drawn < targetWidth) {
            int slice = Math.min(tw - u, targetWidth - drawn);
            guiGraphics.blit(
                    layer.texture(),
                    viewportX + drawn,
                    viewportY,
                    slice,
                    BannerSection.INNER_HEIGHT,
                    (float) u,
                    0.0F,
                    slice,
                    th,
                    tw,
                    th
            );
            drawn += slice;
            u = 0;
        }

        guiGraphics.pose().popPose();
    }

    public static void restoreMode(CreativeModeInventoryScreen screen) {
        if (instance == null) return;
        instance.onChanged = true;
        instance.syncMenu(screen);
    }

    private void syncMenu(CreativeModeInventoryScreen screen) {
        currentCreativeTab = CreativeModeInventoryScreen.selectedTab;
        boolean visible = isCarriageTab(currentCreativeTab);
        boolean preview = CarriageFlowClient.previewMode();
        if (visible && !preview) {
            if (onChanged || !filter.hasAppliedFilter(screen.getMenu())) {
                updateMenuItem(screen.getMenu());
                screen.scrollOffs = groupScroll[CarriageInventoryFilter.carriageType];
                screen.getMenu().scrollTo(groupScroll[CarriageInventoryFilter.carriageType]);
                onChanged = false;
            } else captureLegacyScroll(screen);
        }
        refreshBtn(currentCreativeTab);
        for (int i = 0; i < imgBtn.length; i++) if (imgBtn[i] != null) {
            if ((i == 0 && preview) || (i == CarriageInventoryFilter.carriageType && !preview)) imgBtn[i].toggle();
            else imgBtn[i].reset();
        }
        for (ImageButton button : upAndDownBtn) if (button != null) button.visible = visible && !preview;
    }

    private static boolean isCarriageTab(CreativeModeTab tab) {
        return tab == HexieBridges.modBootstrap().hexieNormalSpeedPassageCarriageTab().getId();
    }

    @SubscribeEvent
    public void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CarriageInventoryFilter.carriageType = 1;
        java.util.Arrays.fill(groupScroll, 0);
        HexieBridges.Client.clientSync().clear();
    }

    @SubscribeEvent
    public void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        playerName = event.getPlayer().getDisplayName().getString();
        generateSchematicTab();
    }

    @SubscribeEvent
    @SuppressWarnings({"resource"})
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof willow.train.hexie.ui.flow.CarriageFlowPaletteScreen) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        this.guiLeft = ((CreativeModeInventoryScreen) event.getScreen()).getGuiLeft();
        this.guiTop = ((CreativeModeInventoryScreen) event.getScreen()).getGuiTop();
        CreativeModeInventoryScreen.ItemPickerMenu menu =
                ((CreativeModeInventoryScreen) event.getScreen()).getMenu();
        CreativeModeTab tab = CreativeModeInventoryScreen.selectedTab;
        currentCreativeTab = tab;
        filter.ensureMapping();

        if (tab == HexieBridges.modBootstrap().hexieMain().getId()) {
            addSchematicToTab(menu);
        }

        ItemStack[] icons = new ItemStack[8];
        icons[0] = new ItemStack(HexieBridges.worldRegistry().blockInit().crLogo());
        icons[1] = new ItemStack(HexieBridges.worldRegistry().blockInit().panel25bOriginalBottom());
        icons[2] = new ItemStack(HexieBridges.worldRegistry().blockInit().panel25gOriginalBottom());
        icons[3] = new ItemStack(HexieBridges.worldRegistry().blockInit().panel25kOriginalBottom());
        icons[4] = new ItemStack(HexieBridges.worldRegistry().blockInit().panel25zOriginalBottomSide());
        icons[5] = new ItemStack(HexieBridges.worldRegistry().logoItemInit().logoA25t());
        icons[6] = new ItemStack(HexieBridges.worldRegistry().blockInit().panel25bMarshalledSymbol());
        icons[7] = new ItemStack(HexieBridges.worldRegistry().blockInit().panelCr200jMarshalledBottom());

        upAndDownBtn[0] = new ImageButton(new RenderablePicture(UP_IMAGE), this.guiLeft - 22, this.guiTop - 8, 20, 20,
                b -> {
                    if(btn_location > 0){
                        btn_location --;
                        btn_location = Math.max(btn_location, 0);
                        refreshBtn(currentCreativeTab);

                    }
                });
        upAndDownBtn[1] = new ImageButton(new RenderablePicture(DOWN_IMAGE), this.guiLeft - 22, this.guiTop - 8 + (showBtnNumber + 1) * 22, 20, 20,
                b -> {
                    if(btn_location < imgBtn.length - showBtnNumber){
                        btn_location++;
                        btn_location = Math.min(btn_location, imgBtn.length - showBtnNumber);
                        refreshBtn(currentCreativeTab);

                    }
                });

        initMapping();

        Component[] components = new Component[8];
        components[0] =
                Component.translatable("container." + HexieConstants.MOD_ID + ".inventory.button.switch_mode");
        components[1] =
                Component.translatable("container." + HexieConstants.MOD_ID + ".inventory.button.25b");
        components[2] =
                Component.translatable("container." + HexieConstants.MOD_ID + ".inventory.button.25g");
        components[3] =
                Component.translatable("container." + HexieConstants.MOD_ID + ".inventory.button.25k");
        components[4] =
                Component.translatable("container." + HexieConstants.MOD_ID + ".inventory.button.25z");
        components[5] =
                Component.translatable("container." + HexieConstants.MOD_ID + ".inventory.button.25t");
        components[6] =
                Component.translatable(
                        "container." + HexieConstants.MOD_ID + ".inventory.button.marshalled_25_series");
        components[7] =
                Component.translatable("container." + HexieConstants.MOD_ID + ".inventory.button.cr200j");

        for (int i = 0; i < imgBtn.length; i++) {
            imgBtn[i] =
                    new ItemIconButton(
                            this.guiLeft - 22,
                            this.guiTop  + 14 + (i - btn_location) * 22,
                            components[i],
                            icons[i],
                            b -> {
                                Screen activeScreen = Minecraft.getInstance().screen;
                                if (!(activeScreen instanceof CreativeModeInventoryScreen creativeScreen)) return;
                                if (b == imgBtn[0]) {
                                    CarriageFlowClient.toggleMode(creativeScreen);
                                    return;
                                }
                                captureLegacyScroll(creativeScreen);
                                for (int bx = 1; bx < imgBtn.length; bx++) if (b == imgBtn[bx]) CarriageInventoryFilter.carriageType = bx;
                                onChanged = true;
                                syncMenu(creativeScreen);
                            },
                            i == 0 ? -2 : 1,
                            i == 0 ? 0 : 2);
        }
        imgBtn[0].setExpandWhenSelected(false);
        imgBtn[0].setTooltip(net.minecraft.client.gui.components.Tooltip.create(components[0]));
        imgBtn[0].setMessage(components[0]);
        for (ItemIconButton b : imgBtn) {
            event.addListener(b);
        }
        refreshBtn(tab);
        for(ImageButton b : upAndDownBtn) {
            event.addListener(b);
            b.visible = isCarriageTab(tab);
        }

        onChanged = true;
        syncMenu((CreativeModeInventoryScreen) event.getScreen());
    }

    private void refreshBtn(net.minecraft.world.item.CreativeModeTab tab) {
        boolean visible = isCarriageTab(tab);
        if (imgBtn[0] != null) {
            imgBtn[0].visible = visible && Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen screen
                    && CarriageFlowClient.canSwitchMode(screen);
            imgBtn[0].setY(guiTop + 14);
        }
        for (int i = 1; i < imgBtn.length; i++) if (imgBtn[i] != null) {
            imgBtn[i].setY(guiTop + 36 + (i - 1 - btn_location) * 22);
            imgBtn[i].visible = visible && !CarriageFlowClient.previewMode()
                    && i > btn_location && i < btn_location + showBtnNumber;
        }
    }

    @SubscribeEvent
    public void onRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof willow.train.hexie.ui.flow.CarriageFlowPaletteScreen) return;
        if (event.getScreen() instanceof CreativeModeInventoryScreen screen) {
            syncMenu(screen);
            if (currentCreativeTab == HexieBridges.modBootstrap().hexieMain().getId()) addSchematicToTab(screen.getMenu());
        }
    }


    public void updateMenuItem(CreativeModeInventoryScreen.ItemPickerMenu menu) {
        filter.updateMenuItem(menu);
    }

    public void initMapping() {
        filter.initMapping();
    }

    public void addSchematicToTab(CreativeModeInventoryScreen.ItemPickerMenu menu) {
        schematics.addSchematicToTab(menu);
    }

    public void generateSchematicTab() {
        schematics.generateSchematicTab();
    }
}
