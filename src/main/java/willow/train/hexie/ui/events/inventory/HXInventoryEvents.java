package willow.train.hexie.ui.events.inventory;

// split from willow/train/hexie/event/client/HXInventoryEvents.java

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
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
import willow.train.hexie.ui.flow.CarriageFlowClient;
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
        if (instance == null || screen instanceof willow.train.hexie.ui.flow.CarriageFlowPaletteScreen
                || CarriageFlowClient.previewMode()) return;
        if (isCarriageTab(CreativeModeInventoryScreen.selectedTab) && instance.filter.hasAppliedFilter(screen.getMenu()))
            groupScroll[CarriageInventoryFilter.carriageType] = screen.scrollOffs;
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
