package willow.train.hexie.client.carriage.browser;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import willow.train.hexie.mixins.mixin.client.CreativeModeInventoryScreenAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import willow.train.hexie.Main;

import java.io.IOException;

public final class CarriageBrowserClient {
    public static volatile int resourceGeneration;
    private static final Map<net.minecraft.resources.ResourceLocation, Boolean> MODES = new java.util.HashMap<>();
    private static final Map<net.minecraft.resources.ResourceLocation, Float> ORIGINAL_SCROLL = new java.util.HashMap<>();
    private static final Map<CreativeModeInventoryScreen, net.minecraft.resources.ResourceLocation> OBSERVED = new java.util.WeakHashMap<>();
    private static final Map<CreativeModeInventoryScreen, net.minecraft.client.gui.components.Button> SWITCHES = new java.util.WeakHashMap<>();

    public static net.minecraft.resources.ResourceLocation tabId(CreativeModeInventoryScreen screen) {
        return net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getKey(
                ((CreativeModeInventoryScreenAccessor) screen).hexie$getSelectedTab());
    }

    public static CarriagePages.Page page(CreativeModeInventoryScreen screen) {
        if (((CreativeModeInventoryScreenAccessor) screen).hexie$getSelectedTab().getType()
                != net.minecraft.world.item.CreativeModeTab.Type.CATEGORY) return null;
        return CarriagePages.get(net.minecraft.client.Minecraft.getInstance().getResourceManager(), tabId(screen));
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
        return ((CreativeModeInventoryScreenAccessor) screen).hexie$getSelectedTab() == Main.HEXIE_NORMAL_SPEED_PASSAGE_CARRIAGE_TAB.getId();
    }

    public static void captureOriginal(CreativeModeInventoryScreen screen) {
        if (screen instanceof CarriagePaletteScreen || hexieTab(screen) || previewMode(screen)) return;
        var id = tabId(screen);
        if (id != null && id.equals(OBSERVED.get(screen)))
            ORIGINAL_SCROLL.put(id, ((CreativeModeInventoryScreenAccessor) screen).hexie$getScrollOffs());
    }

    public static void toggleMode(CreativeModeInventoryScreen screen) {
        if (!canSwitchMode(screen)) return;
        boolean preview = previewMode(screen);
        removed(screen);
        MODES.put(tabId(screen), !preview);
        OBSERVED.remove(screen);
        var accessor = (CreativeModeInventoryScreenAccessor) screen;
        accessor.hexie$selectTab(accessor.hexie$getSelectedTab());
        if (hexieTab(screen)) willow.train.hexie.event.client.HXInventoryEvents.restoreMode(screen);
        syncOriginal(screen);
    }

    private static void initScreen(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || screen instanceof CarriagePaletteScreen) return;
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
            ((CreativeModeInventoryScreenAccessor) screen).hexie$setScrollOffs(scroll);
            screen.getMenu().scrollTo(scroll);
            OBSERVED.put(screen, id);
        } else captureOriginal(screen);
    }

    private static void beforeRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof CreativeModeInventoryScreen screen && !(screen instanceof CarriagePaletteScreen)) syncOriginal(screen);
    }

    private static final Map<CreativeModeInventoryScreen, CarriageBrowserView> VIEWS = new IdentityHashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(CarriageBrowserClient::shaders);
        bus.addListener(CarriageBrowserClient::reloads);
        NeoForge.EVENT_BUS.addListener(CarriageBrowserClient::initScreen);
        NeoForge.EVENT_BUS.addListener(CarriageBrowserClient::beforeRender);
        NeoForge.EVENT_BUS.addListener(CarriageBrowserClient::key);
        NeoForge.EVENT_BUS.addListener(CarriageBrowserClient::click);
        NeoForge.EVENT_BUS.addListener(CarriageBrowserClient::scroll);
    }

    private static void shaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Main.asResource("carriage_preview"),
                    DefaultVertexFormat.POSITION_TEX), shader -> CarriagePreview.shader = shader);
        } catch (IOException e) { throw new IllegalStateException("Cannot load carriage preview shader", e); }
    }

    private static void reloads(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> resourceGeneration++);
    }

    public static boolean active(Screen screen) {
        return screen instanceof CreativeModeInventoryScreen creative
                && !(screen instanceof CarriagePaletteScreen) && previewMode(creative);
    }

    public static void render(CreativeModeInventoryScreen screen, GuiGraphics gui, int x, int y) {
        if (!active(screen)) { removed(screen); return; }
        var previous = VIEWS.get(screen);
        if (previous != null && !previous.tab.equals(tabId(screen))) removed(screen);
        VIEWS.computeIfAbsent(screen, CarriageBrowserView::new).render(gui, x, y);
    }

    public static void removed(CreativeModeInventoryScreen screen) {
        captureOriginal(screen);
        willow.train.hexie.event.client.HXInventoryEvents.captureLegacyScroll(screen);
        CarriageBrowserView view = VIEWS.get(screen);
        if (view != null && !view.openingPalette) {
            VIEWS.remove(screen);
            view.close();
        }
    }

    static void release(CarriageBrowserView view) {
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
