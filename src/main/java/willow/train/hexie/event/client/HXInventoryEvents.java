package willow.train.hexie.event.client;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;

import willow.train.hexie.Main;
import willow.train.hexie.client.carriage.browser.CarriageBrowserClient;
import willow.train.hexie.client.screens.widget.ImageButton;
import willow.train.hexie.client.screens.widget.ItemIconButton;
import willow.train.hexie.client.carriage.*;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.client.util.GuiImageCache;
import willow.train.hexie.init.item.LogoItemInit;
import willow.train.hexie.mixins.mixin.client.CreativeModeInventoryScreenAccessor;
import willow.train.hexie.network.s2c.ContraptionSeatPositionCache;
import willow.train.hexie.client.util.RenderablePicture;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class HXInventoryEvents {
    public static final GuiImageCache.ImageData UP_IMAGE = GuiImageCache.register("textures/gui/up_button.png", 0);
    public static final GuiImageCache.ImageData DOWN_IMAGE = GuiImageCache.register("textures/gui/down_button.png", 0);
    private static int carriageType = 1;
    private static final float[] groupScroll = new float[8];
    private static CreativeModeTab currentCreativeTab = CreativeModeTabs.getDefaultTab();
    private static HXInventoryEvents instance;
    ItemIconButton[] imgBtn = new ItemIconButton[8];
    ImageButton[] upAndDownBtn = new ImageButton[2];
    ArrayList<List<ItemStack>> itemList = new ArrayList<>();
    private List<ItemStack> appliedItems = List.of();
    private boolean mappingInitialized;
    List<ItemStack> schematicItemList = new ArrayList<>();
    int btn_location = 0;
    int showBtnNumber = 5;
    int guiLeft = 0, guiTop = 0;
    boolean onChanged = true;
    String playerName = "";

    public HXInventoryEvents() {
        instance = this;
    }


    public static void captureLegacyScroll(CreativeModeInventoryScreen screen) {
        if (instance == null || screen instanceof willow.train.hexie.client.carriage.browser.CarriagePaletteScreen
                || CarriageBrowserClient.previewMode()) return;
        var accessor = (CreativeModeInventoryScreenAccessor) screen;
        if (isCarriageTab(accessor.hexie$getSelectedTab()) && instance.hasAppliedFilter(screen.getMenu()))
            groupScroll[carriageType] = accessor.hexie$getScrollOffs();
    }

    public static void restoreMode(CreativeModeInventoryScreen screen) {
        if (instance == null) return;
        instance.onChanged = true;
        instance.syncMenu(screen);
    }

    private void syncMenu(CreativeModeInventoryScreen screen) {
        var accessor = (CreativeModeInventoryScreenAccessor) screen;
        currentCreativeTab = accessor.hexie$getSelectedTab();
        boolean visible = isCarriageTab(currentCreativeTab);
        boolean preview = CarriageBrowserClient.previewMode();
        if (visible && !preview) {
            if (onChanged || !hasAppliedFilter(screen.getMenu())) {
                updateMenuItem(screen.getMenu());
                accessor.hexie$setScrollOffs(groupScroll[carriageType]);
                screen.getMenu().scrollTo(groupScroll[carriageType]);
                onChanged = false;
            } else captureLegacyScroll(screen);
        }
        refreshBtn(currentCreativeTab);
        for (int i = 0; i < imgBtn.length; i++) if (imgBtn[i] != null) {
            if ((i == 0 && preview) || (i == carriageType && !preview)) imgBtn[i].toggle();
            else imgBtn[i].reset();
        }
        for (ImageButton button : upAndDownBtn) if (button != null) button.visible = visible && !preview;
    }

    private static boolean isCarriageTab(CreativeModeTab tab) {
        return tab == Main.HEXIE_NORMAL_SPEED_PASSAGE_CARRIAGE_TAB.getId();
    }

    @SubscribeEvent
    public void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        carriageType = 1;
        java.util.Arrays.fill(groupScroll, 0);
        ContraptionSeatPositionCache.clear();
    }

    @SubscribeEvent
    public void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        playerName = event.getPlayer().getDisplayName().getString();
        generateSchematicTab();
    }

    @SubscribeEvent
    @SuppressWarnings({"resource"})
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof willow.train.hexie.client.carriage.browser.CarriagePaletteScreen) return;
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        this.guiLeft = ((CreativeModeInventoryScreen) event.getScreen()).getGuiLeft();
        this.guiTop = ((CreativeModeInventoryScreen) event.getScreen()).getGuiTop();
        CreativeModeInventoryScreen.ItemPickerMenu menu =
                ((CreativeModeInventoryScreen) event.getScreen()).getMenu();
        CreativeModeTab tab = ((CreativeModeInventoryScreenAccessor) event.getScreen())
                .hexie$getSelectedTab();
        currentCreativeTab = tab;
        ensureMapping();

        if (tab == Main.HEXIE_MAIN.getId()) {
            addSchematicToTab(menu);
        }

        ItemStack[] icons = new ItemStack[8];
        icons[0] = new ItemStack(BlockInit.CR_LOGO.get());
        icons[1] = new ItemStack(BlockInit.PANEL_25B_ORIGINAL_BOTTOM.get());
        icons[2] = new ItemStack(BlockInit.PANEL_25G_ORIGINAL_BOTTOM.get());
        icons[3] = new ItemStack(BlockInit.PANEL_25K_ORIGINAL_BOTTOM.get());
        icons[4] = new ItemStack(BlockInit.PANEL_25Z_ORIGINAL_BOTTOM_SIDE.get());
        icons[5] = new ItemStack(LogoItemInit.LOGO_A25T.get());
        icons[6] = new ItemStack(BlockInit.PANEL_25B_MARSHALLED_SYMBOL.get());
        icons[7] = new ItemStack(BlockInit.PANEL_CR200J_MARSHALLED_BOTTOM.get());

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
                Component.translatable("container." + Main.MOD_ID + ".inventory.button.switch_mode");
        components[1] =
                Component.translatable("container." + Main.MOD_ID + ".inventory.button.25b");
        components[2] =
                Component.translatable("container." + Main.MOD_ID + ".inventory.button.25g");
        components[3] =
                Component.translatable("container." + Main.MOD_ID + ".inventory.button.25k");
        components[4] =
                Component.translatable("container." + Main.MOD_ID + ".inventory.button.25z");
        components[5] =
                Component.translatable("container." + Main.MOD_ID + ".inventory.button.25t");
        components[6] =
                Component.translatable(
                        "container." + Main.MOD_ID + ".inventory.button.marshalled_25_series");
        components[7] =
                Component.translatable("container." + Main.MOD_ID + ".inventory.button.cr200j");

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
                                    CarriageBrowserClient.toggleMode(creativeScreen);
                                    return;
                                }
                                captureLegacyScroll(creativeScreen);
                                for (int bx = 1; bx < imgBtn.length; bx++) if (b == imgBtn[bx]) carriageType = bx;
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
                    && CarriageBrowserClient.canSwitchMode(screen);
            imgBtn[0].setY(guiTop + 14);
        }
        for (int i = 1; i < imgBtn.length; i++) if (imgBtn[i] != null) {
            imgBtn[i].setY(guiTop + 36 + (i - 1 - btn_location) * 22);
            imgBtn[i].visible = visible && !CarriageBrowserClient.previewMode()
                    && i > btn_location && i < btn_location + showBtnNumber;
        }
    }

    @SubscribeEvent
    public void onRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof willow.train.hexie.client.carriage.browser.CarriagePaletteScreen) return;
        if (event.getScreen() instanceof CreativeModeInventoryScreen screen) {
            syncMenu(screen);
            if (currentCreativeTab == Main.HEXIE_MAIN.getId()) addSchematicToTab(screen.getMenu());
        }
    }

    public void updateMenuItem(CreativeModeInventoryScreen.ItemPickerMenu menu) {
        ensureMapping();
        switch (carriageType) {
            case 1: // B
                menu.items.clear();
                menu.items.addAll(itemList.get(0));
                menu.items.addAll(itemList.get(7));
                menu.items.addAll(itemList.get(8));
                break;
            case 2: // G
                menu.items.clear();
                menu.items.addAll(itemList.get(1));
                menu.items.addAll(itemList.get(7));
                menu.items.addAll(itemList.get(8));
                break;
            case 3: // K
                menu.items.clear();
                menu.items.addAll(itemList.get(2));
                menu.items.addAll(itemList.get(7));
                menu.items.addAll(itemList.get(8));
                break;
            case 4: // Z
                menu.items.clear();
                menu.items.addAll(itemList.get(3));
                menu.items.addAll(itemList.get(7));
                menu.items.addAll(itemList.get(8));
                break;
            case 5: // T
                menu.items.clear();
                menu.items.addAll(itemList.get(4));
                menu.items.addAll(itemList.get(7));
                break;
            case 6: // M
                menu.items.clear();
                menu.items.addAll(itemList.get(5));
                menu.items.addAll(itemList.get(7));
                break;
            case 7: // M
                menu.items.clear();
                menu.items.addAll(itemList.get(6));
                menu.items.addAll(itemList.get(7));
                break;
            default:
                menu.items.clear();
                menu.items.addAll(itemList.get(0)); // B
                menu.items.addAll(itemList.get(1)); // G
                menu.items.addAll(itemList.get(2)); // K
                menu.items.addAll(itemList.get(3)); // Z
                menu.items.addAll(itemList.get(4)); // T
                menu.items.addAll(itemList.get(5)); // M
                menu.items.addAll(itemList.get(6));  // cr200j
                menu.items.addAll(itemList.get(7)); // general
                menu.items.addAll(itemList.get(8)); // bgzk
        }
        rememberAppliedItems(menu);
    }

    private boolean hasAppliedFilter(CreativeModeInventoryScreen.ItemPickerMenu menu) {
        if (appliedItems.size() != menu.items.size()) return false;
        for (int index = 0; index < appliedItems.size(); index++) {
            if (appliedItems.get(index) != menu.items.get(index)) return false;
        }
        return true;
    }

    private void rememberAppliedItems(CreativeModeInventoryScreen.ItemPickerMenu menu) {
        appliedItems = new ArrayList<>(menu.items);
    }

    public void initMapping() {
        if (mappingInitialized) return;
        itemList =
                new ArrayList<>() {
                    {
                        add(Carriage25B.getList());  // 25B 0
                        add(Carriage25G.getList());  // 25G 1
                        add(Carriage25K.getList());  // 25K 2
                        add(Carriage25Z.getList());  // 25Z 3
                        add(Carriage25T.getList());  // 25T 4
                        add(Marshalled25Series.getList());  // 25 Marshalled Series 5
                        add(CarriageCR200j.getList()); // cr200j 6
                        add(
                                List.of(
                                        // BlockInit.ORIGINAL_COLOR_WINDOW_25.get().asItem().getDefaultInstance(),
                                        // BlockInit.ORIGINAL_COLOR_WINDOW_25_SEALED.get().asItem().getDefaultInstance()
                                        )); // 通用 7

                        add(Carriage25BGZK.getList()); // BGZK 8
                    }
                };
        mappingInitialized = true;
    }

    private void ensureMapping() {
        if (!mappingInitialized) initMapping();
    }

    public void addSchematicToTab(CreativeModeInventoryScreen.ItemPickerMenu menu) {
        if (!schematicItemList.isEmpty()) {
            for (ItemStack stack : schematicItemList) {
                if (!menu.items.contains(stack)) {
                    menu.items.add(stack);
                }
            }
            //menu.scrollTo(1);
        }
    }

    public void generateSchematicTab() {
        try {
            // TODO: for next version (0.4)
        } catch (Exception e) {
            Main.LOGGER.error("FATAL ERROR IN LOADING HEXIE SCHEMATICS ", e);
        }
    }
}
