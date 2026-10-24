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
    private static int carriageType = 0;
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


    private static void onCreativeTabObserved(
            CreativeModeInventoryScreen screen, CreativeModeTab selectedTab) {
        boolean tabChanged = currentCreativeTab != selectedTab;
        currentCreativeTab = selectedTab;
        HXInventoryEvents events = instance;
        if (events == null) return;

        if (!isCarriageTab(selectedTab)) {
            events.onChanged = true;
            return;
        }

        CreativeModeInventoryScreen.ItemPickerMenu menu = screen.getMenu();
        if (tabChanged || events.onChanged || !events.hasAppliedFilter(menu)) {
            events.updateMenuItem(menu);
            if (tabChanged || events.onChanged) menu.scrollTo(0.0F);
            events.onChanged = false;
        }
    }

    private static boolean isCarriageTab(CreativeModeTab tab) {
        return tab == Main.HEXIE_NORMAL_SPEED_PASSAGE_CARRIAGE_TAB.getId();
    }

    @SubscribeEvent
    public void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        carriageType = 0;
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
                        refreshBtn(tab);
                        onUp();
                    }
                });
        upAndDownBtn[1] = new ImageButton(new RenderablePicture(DOWN_IMAGE), this.guiLeft - 22, this.guiTop - 8 + (showBtnNumber + 1) * 22, 20, 20,
                b -> {
                    if(btn_location < imgBtn.length - showBtnNumber){
                        btn_location++;
                        btn_location = Math.min(btn_location, imgBtn.length - showBtnNumber);
                        refreshBtn(tab);
                        onDown();
                    }
                });

        initMapping();

        Component[] components = new Component[8];
        components[0] =
                Component.translatable("container." + Main.MOD_ID + ".inventory.button.all");
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
                                for (int bx = 0; bx < imgBtn.length; bx++) {
                                    if (b.equals(imgBtn[bx])) {
                                        carriageType = bx;
                                        ((ItemIconButton) b).toggle();
                                        Screen activeScreen = Minecraft.getInstance().screen;
                                        if (activeScreen instanceof CreativeModeInventoryScreen creativeScreen
                                                && isCarriageTab(currentCreativeTab)) {
                                            ensureMapping();
                                            updateMenuItem(creativeScreen.getMenu());
                                            creativeScreen.getMenu().scrollTo(0.0F);
                                            onChanged = false;
                                        }
                                    } else {
                                        (imgBtn[bx]).reset();
                                    }
                                }
                            },
                            i == 0 ? -2 : 1,
                            i == 0 ? 0 : 2);
        }
        for (ItemIconButton b : imgBtn) {
            event.addListener(b);
        }
        refreshBtn(tab);
        for(ImageButton b : upAndDownBtn) {
            event.addListener(b);
            b.visible = isCarriageTab(tab);
        }

        imgBtn[carriageType].toggle();
        updateButtons();
        menu.scrollTo(0);
    }

    private void refreshBtn(net.minecraft.world.item.CreativeModeTab tab) {
        int right_edge = btn_location + showBtnNumber - 1;
        for(int i = 0; i < imgBtn.length; i++) {
            if(i < btn_location || i > right_edge) {
                imgBtn[i].visible = false;
            } else {
                imgBtn[i].visible = isCarriageTab(tab);
            }
        }
    }

    private void onUp() {
        for(ItemIconButton b : imgBtn) {
            b.setY(b.getY() + 22);
        }
    }

    private void onDown() {
        for(ItemIconButton b : imgBtn) {
            b.setY(b.getY() - 22);
        }
    }

    @SubscribeEvent
    public void onRender(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
            onCreativeTabObserved(
                    creativeScreen,
                    ((CreativeModeInventoryScreenAccessor) creativeScreen).hexie$getSelectedTab());
            updateButtons();
            CreativeModeTab tab = currentCreativeTab;
            for(ImageButton b : upAndDownBtn) {
                b.visible = isCarriageTab(tab);
            }
            refreshBtn(tab);
            if (tab == Main.HEXIE_MAIN.getId()) {
                addSchematicToTab(((CreativeModeInventoryScreen) screen).getMenu());
            }
        }
    }

    public void updateButtons() {
        Screen screen = Minecraft.getInstance().screen;
        CreativeModeTab tab = currentCreativeTab;
        if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
            CreativeModeInventoryScreen.ItemPickerMenu menu = creativeScreen.getMenu();
            boolean vis = isCarriageTab(tab);
            for (ItemIconButton b : imgBtn) {
                b.visible = vis;
            }
            if (vis) {
                if (onChanged) {
                    menu.scrollTo(0.0f);
                    updateMenuItem(menu);
                    menu.scrollTo(0.0f);
                    onChanged = false;
                }
            } else {
                onChanged = true;
            }
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
