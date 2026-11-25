package willow.train.hexie.ui.flow;

// split from willow/train/hexie/client/carriage/flow/CarriageFlowClient.java

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import willow.train.hexie.bridge.HexieBridges;

public final class CarriageFlowClient {
    public static volatile int resourceGeneration;
    private static final Map<net.minecraft.resources.ResourceLocation, Boolean> MODES = new java.util.HashMap<>();
    private static final Map<net.minecraft.resources.ResourceLocation, Float> ORIGINAL_SCROLL = new java.util.HashMap<>();
    private static final Map<CreativeModeInventoryScreen, net.minecraft.resources.ResourceLocation> OBSERVED = new java.util.WeakHashMap<>();
    private static final Map<CreativeModeInventoryScreen, net.minecraft.client.gui.components.Button> SWITCHES = new java.util.WeakHashMap<>();

    public static net.minecraft.resources.ResourceLocation tabId(CreativeModeInventoryScreen screen) {
        return net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getKey(
                CreativeModeInventoryScreen.selectedTab);
    }

    public static CarriageFlowPages.Page page(CreativeModeInventoryScreen screen) {
        if (CreativeModeInventoryScreen.selectedTab.getType()
                != net.minecraft.world.item.CreativeModeTab.Type.CATEGORY) return null;
        return CarriageFlowPages.get(tabId(screen));
    }

    public static boolean previewMode() {
        return net.minecraft.client.Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen screen && previewMode(screen);
    }

    private static boolean previewMode(CreativeModeInventoryScreen screen) {
        var page = page(screen);
        return page != null && MODES.getOrDefault(page.tab(), page.preview());
    }

    public static boolean canSwitchMode(CreativeModeInventoryScreen screen) {
        var page = page(screen);
        return page != null && page.switchable();
    }

    private static boolean hexieTab(CreativeModeInventoryScreen screen) {
        return CreativeModeInventoryScreen.selectedTab == HexieBridges.modBootstrap().hexieNormalSpeedPassageCarriageTab().getId();
    }

    public static void captureOriginal(CreativeModeInventoryScreen screen) {
        if (screen instanceof CarriageFlowPaletteScreen || hexieTab(screen) || previewMode(screen)) return;
        var id = tabId(screen);
        if (id != null && id.equals(OBSERVED.get(screen)))
            ORIGINAL_SCROLL.put(id, screen.scrollOffs);
    }

    public static void toggleMode(CreativeModeInventoryScreen screen) {
        if (!canSwitchMode(screen)) return;
        boolean preview = previewMode(screen);
        removed(screen);
        MODES.put(tabId(screen), !preview);
        OBSERVED.remove(screen);
        screen.selectTab(CreativeModeInventoryScreen.selectedTab);
        if (hexieTab(screen)) willow.train.hexie.ui.events.inventory.HXInventoryEvents.restoreMode(screen);
        syncOriginal(screen);
    }

    private static void initScreen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || screen instanceof CarriageFlowPaletteScreen) return;
        var button = net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("↔"), b -> toggleMode(screen))
                .bounds(screen.getGuiLeft() - 22, screen.getGuiTop() + 14, 20, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.translatable("container.hexie.inventory.button.switch_mode"))).build();
        button.visible = !hexieTab(screen) && canSwitchMode(screen);
        SWITCHES.put(screen, button);
        event.addListener(button);
        OBSERVED.remove(screen);
    }

    private static void syncOriginal(CreativeModeInventoryScreen screen) {
        var button = SWITCHES.get(screen);
        if (button != null) button.visible = !hexieTab(screen) && canSwitchMode(screen);
        if (hexieTab(screen) || page(screen) == null || previewMode(screen)) { OBSERVED.remove(screen); return; }
        var id = tabId(screen);
        if (!id.equals(OBSERVED.get(screen))) {
            float scroll = ORIGINAL_SCROLL.getOrDefault(id, 0f);
            screen.scrollOffs = scroll;
            screen.getMenu().scrollTo(scroll);
            OBSERVED.put(screen, id);
        } else captureOriginal(screen);
    }

    private static void beforeRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof CreativeModeInventoryScreen screen && !(screen instanceof CarriageFlowPaletteScreen)) syncOriginal(screen);
    }

    private static final Map<CreativeModeInventoryScreen, CarriageFlowView> VIEWS = new IdentityHashMap<>();

    static void closeViews() {
        VIEWS.values().forEach(CarriageFlowView::close);
        VIEWS.clear();
    }

    public static void register(IEventBus bus) {
        CarriageFlowSchematicIcons.register(bus);
        if (!ModList.get().isLoaded("create")) bus.addListener(CarriageFlowModels::register);
        bus.addListener(CarriageFlowPreviewCache::shaders);
        bus.addListener(CarriageFlowPreviewCache::reloads);
        NeoForge.EVENT_BUS.addListener(CarriageFlowPreviewCache::tickCache);
        NeoForge.EVENT_BUS.addListener(CarriageFlowClient::initScreen);
        NeoForge.EVENT_BUS.addListener(CarriageFlowClient::beforeRender);
        NeoForge.EVENT_BUS.addListener(CarriageFlowClient::key);
        NeoForge.EVENT_BUS.addListener(CarriageFlowClient::click);
        NeoForge.EVENT_BUS.addListener(CarriageFlowClient::drag);
        NeoForge.EVENT_BUS.addListener(CarriageFlowClient::releaseMouse);
        NeoForge.EVENT_BUS.addListener(CarriageFlowClient::scroll);
    }

    public static boolean active(Screen screen) {
        return screen instanceof CreativeModeInventoryScreen creative
                && !(screen instanceof CarriageFlowPaletteScreen) && previewMode(creative);
    }

    public static boolean hiddenSlot(Screen screen, net.minecraft.world.inventory.Slot slot) {
        if (slot == null || slot.index >= 45 || !active(screen)) return false;
        var view = VIEWS.get(screen);
        return view == null || !view.isSchematicSlot(slot);
    }

    public static void restoreSlots(CreativeModeInventoryScreen screen) {
        var view = VIEWS.get(screen);
        if (view != null) view.restoreSchematicSlot();
    }

    public static void render(CreativeModeInventoryScreen screen, GuiGraphics gui, int x, int y) {
        if (!active(screen)) { removed(screen); return; }
        var previous = VIEWS.get(screen);
        if (previous != null && !previous.tab.equals(tabId(screen))) removed(screen);
        VIEWS.computeIfAbsent(screen, CarriageFlowView::new).render(gui, x, y);
    }

    public static void removed(CreativeModeInventoryScreen screen) {
        captureOriginal(screen);
        willow.train.hexie.ui.events.inventory.HXInventoryEvents.captureLegacyScroll(screen);
        CarriageFlowView view = VIEWS.get(screen);
        if (view != null && !view.openingPalette) {
            VIEWS.remove(screen);
            view.close();
        }
    }

    static void release(CarriageFlowView view) {
        VIEWS.remove(view.host);
        view.close();
    }

    private static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!active(event.getScreen())) return;
        var screen = (CreativeModeInventoryScreen) event.getScreen();
        var view = VIEWS.get(screen);
        double x = event.getMouseX() - screen.getGuiLeft(), y = event.getMouseY() - screen.getGuiTop();
        if (view != null && view.contains(x, y)) {
            view.mouseClicked(x, y, event.getButton());
            event.setCanceled(true);
        }
    }

    private static void drag(ScreenEvent.MouseDragged.Pre event) {
        if (!active(event.getScreen())) return;
        var view = VIEWS.get(event.getScreen());
        // Keep the captured gesture while the pointer leaves the preview or crosses the carriage.
        if (view != null && view.mouseDragged(
                event.getMouseX() - view.host.getGuiLeft(), event.getMouseY() - view.host.getGuiTop(),
                event.getMouseButton(), event.getDragX(), event.getDragY()))
            event.setCanceled(true);
    }

    private static void releaseMouse(ScreenEvent.MouseButtonReleased.Pre event) {
        var view = VIEWS.get(event.getScreen());
        if (view != null && view.mouseReleased(
                event.getMouseX() - view.host.getGuiLeft(), event.getMouseY() - view.host.getGuiTop(),
                event.getButton()))
            event.setCanceled(true);
    }

    private static void scroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!active(event.getScreen())) return;
        var screen = (CreativeModeInventoryScreen) event.getScreen();
        var view = VIEWS.get(screen);
        if (view != null && view.contains(event.getMouseX() - screen.getGuiLeft(), event.getMouseY() - screen.getGuiTop())) {
            view.mouseScrolled(event.getMouseX() - screen.getGuiLeft(), event.getMouseY() - screen.getGuiTop(),
                    event.getScrollDeltaX(), event.getScrollDeltaY());
            event.setCanceled(true);
        }
    }

    private static void key(ScreenEvent.KeyPressed.Pre event) {
        if (!active(event.getScreen())) return;
        var view = VIEWS.get(event.getScreen());
        if (view != null && view.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers()))
            event.setCanceled(true);
    }
}
