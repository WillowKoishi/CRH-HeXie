package willow.train.hexie.client.events;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.api.cab.CabControl;
import willow.train.hexie.api.cab.CabSurfaceHost;
import willow.train.hexie.api.cab.CabSurfaceHost.KeyboardAction;
import willow.train.hexie.api.cab.CabSurfaceHost.KeyboardBinding;
import willow.train.hexie.api.cab.CabSurfaceHost.MouseAction;
import willow.train.hexie.api.cab.CabSurfaceHost.MouseBinding;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.CabControlPacket;
import willow.train.hexie.network.c2s.CabKeyboardPacket;
import willow.train.hexie.client.cab.CabPresentation;

@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class DrivingConsoleClient {
    private static DrivingConsoleContext context;
    private static boolean pointer;
    private static CabHandleDrag drag;
    private static final Set<InputConstants.Key> capturedKeys = new HashSet<>();
    private static final Set<InputConstants.Key> suppressedMappings = new HashSet<>();
    private static int lastMask = -1, inputTicks;
    private static double zoom = 1, renderedFov = 70;

    public static DrivingConsoleContext context() { return context; }
    static boolean draggingHandle() { return drag != null; }

    static DrivingConsoleContext occupiedCab() {
        var player = Minecraft.getInstance().player;
        return context != null && player != null && player.isAlive() && player.getVehicle() == context.seatAccess().vehicle()
                ? context : null;
    }

    static int cabKeyboardMask() { return Math.max(lastMask, 0); }

    public static boolean capturesInput() {
        var mc = Minecraft.getInstance();
        return context != null && mc.screen == null && mc.getOverlay() == null && mc.isWindowActive();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void renderArm(RenderArmEvent event) {
        var mc = Minecraft.getInstance();
        var cab = occupiedCab();
        if (cab == null || event.getPlayer() != mc.player || mc.getCameraEntity() != mc.player
                || !mc.options.getCameraType().isFirstPerson()) return;
        if (!cab.simple()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        var mc = Minecraft.getInstance();
        var next = DrivingConsoleContext.find(mc.player);
        if (!java.util.Objects.equals(context, next)) {
            boolean wasActive = context != null;
            if (drag != null && capturesInput()) mc.mouseHandler.handleAccumulatedMovement();
            drag = null;
            releaseInput();
            releaseMappings();
            context = next; pointer = false; zoom = 1;
            lastMask = -1; inputTicks = 0;
            if (wasActive || next != null) {
                if (mc.screen == null && mc.isWindowActive() && mc.player != null) mc.mouseHandler.grabMouse();
            }
        }
        if (capturesInput()) {
            var bindings = keyboardBindings();
            int mask = pressedMask(bindings);
            if (bindings.isEmpty()) releaseInput();
            else if (mask != lastMask || ++inputTicks % 10 == 0) sendKeyboard(false, mask);
            // Suppress only mappings whose physical keys this surface actually owns.
            var claimed = new HashSet<InputConstants.Key>();
            for (var binding : bindings) claimed.add(keyFor(binding));
            for (var binding : mouseBindings())
                claimed.add(InputConstants.Type.MOUSE.getOrCreate(binding.button()));
            for (var old : suppressedMappings) if (!claimed.contains(old)) {
                KeyMapping.set(old, physicallyDown(old));
                capturedKeys.remove(old);
            }
            for (var key : claimed) KeyMapping.set(key, false);
            suppressedMappings.clear(); suppressedMappings.addAll(claimed);
            if (drag != null) {
                if (!drag.fits(occupiedCab(), mc.getWindow())
                        || GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), drag.button) != GLFW.GLFW_PRESS)
                    endDrag(mc);
                else drag.update(mc.getWindow());
            }
            boolean showCursor = pointer || drag != null;
            if (showCursor && mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
            if (!showCursor && !mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
        } else {
            drag = null;
            releaseInput();
            releaseMappings();
        }
        SimpleCabTrainHud.tick(context != null && context.simple() ? context : null);
    }

    private static java.util.List<KeyboardBinding> keyboardBindings() {
        if (context == null) return java.util.List.of();
        var state = context.seatAccess().blockState(context.console());
        if (state == null || !(state.getBlock() instanceof CabSurfaceHost host)) return java.util.List.of();
        return host.keyboardBindings(state, context.simple());
    }

    private static InputConstants.Key keyFor(KeyboardBinding binding) {
        return InputConstants.Type.KEYSYM.getOrCreate(binding.key());
    }

    private static java.util.List<MouseBinding> mouseBindings() {
        if (context == null) return java.util.List.of();
        var state = context.seatAccess().blockState(context.console());
        if (state == null || !(state.getBlock() instanceof CabSurfaceHost host)) return java.util.List.of();
        return host.mouseBindings(state, context.simple());
    }

    private static boolean scrollControlsZoom() {
        if (context == null) return false;
        var state = context.seatAccess().blockState(context.console());
        return state != null && state.getBlock() instanceof CabSurfaceHost host
                && host.scrollControlsZoom(state, context.simple());
    }

    private static KeyboardBinding keyboardBinding(int key, int scanCode) {
        var physical = InputConstants.getKey(key, scanCode);
        for (var binding : keyboardBindings()) if (keyFor(binding).equals(physical)) return binding;
        return null;
    }

    private static int pressedMask(java.util.List<KeyboardBinding> bindings) {
        int mask = 0;
        for (var binding : bindings) {
            if (binding.action().inputMask() == 0) continue;
            if (physicallyDown(keyFor(binding))) mask |= binding.action().inputMask();
        }
        return mask;
    }

    private static boolean physicallyDown(InputConstants.Key key) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return key.getType() == InputConstants.Type.MOUSE
                ? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
                : key.getType() == InputConstants.Type.KEYSYM && key.getValue() >= 0
                        && InputConstants.isKeyDown(window, key.getValue());
    }

    private static void sendKeyboard(boolean toggleSimple, int mask) {
        if (context == null || Minecraft.getInstance().getConnection() == null) return;
        HexieNetworkHandler.sendToServer(new CabKeyboardPacket(context.seatAccess().vehicle().getId(), toggleSimple, mask));
        lastMask = mask;
    }

    private static void releaseInput() {
        if (lastMask > 0) sendKeyboard(false, 0);
        lastMask = -1;
    }

    private static void releaseMappings() {
        var mc = Minecraft.getInstance();
        boolean gameplay = mc.screen == null && mc.getOverlay() == null && mc.isWindowActive();
        for (var key : suppressedMappings) {
            KeyMapping.set(key, gameplay && physicallyDown(key));
            capturedKeys.remove(key);
        }
        suppressedMappings.clear();
    }

    /** Every other key continues through Minecraft and other mods unchanged. */
    public static boolean captureKey(int key, int scanCode) {
        return capturesInput() && keyboardBinding(key, scanCode) != null;
    }

    public static boolean handleKey(int key, int scanCode, int action) {
        var physical = InputConstants.getKey(key, scanCode);
        boolean previouslyCaptured = capturedKeys.contains(physical);
        if (action == GLFW.GLFW_RELEASE) capturedKeys.remove(physical);
        if (!captureKey(key, scanCode)) return previouslyCaptured;
        if (action != GLFW.GLFW_RELEASE) capturedKeys.add(physical);
        var operation = new DrivingConsoleKeyEvent(context, key, scanCode, action);
        NeoForge.EVENT_BUS.post(operation);
        if (operation.handled()) return true;
        var binding = keyboardBinding(key, scanCode);
        if (binding == null) return previouslyCaptured;
        if (binding.action() == KeyboardAction.TOGGLE_SIMPLE && action == GLFW.GLFW_PRESS) {
            sendKeyboard(true, pressedMask(keyboardBindings()) & KeyboardAction.APPROACH.inputMask());
        } else if (binding.action() == KeyboardAction.EXIT && action == GLFW.GLFW_PRESS) {
            var player = Minecraft.getInstance().player;
            if (player != null && player.getVehicle() != null) {
                HexieNetworkHandler.sendToServer(
                        new willow.train.hexie.network.c2s.DrivingConsoleExitPacket(player.getVehicle().getId()));
            }
        } else if (binding.action().inputMask() != 0 && action != GLFW.GLFW_REPEAT)
            sendKeyboard(false, pressedMask(keyboardBindings()));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (!capturesInput()) return;
        if (drag != null && event.getButton() == drag.button) {
            event.setCanceled(true);
            if (event.getAction() == GLFW.GLFW_RELEASE) {
                var mc = Minecraft.getInstance();
                if (drag.fits(occupiedCab(), mc.getWindow())) drag.update(mc.getWindow());
                endDrag(mc);
            }
            return;
        }
        var binding = mouseBindings().stream().filter(candidate -> candidate.button() == event.getButton()).findFirst().orElse(null);
        if (binding == null) return;
        event.setCanceled(true);
        if (event.getAction() != GLFW.GLFW_PRESS || drag != null) return;
        if (binding.action() == MouseAction.POINTER) {
            pointer = !pointer;
            if (pointer) Minecraft.getInstance().mouseHandler.releaseMouse();
            else Minecraft.getInstance().mouseHandler.grabMouse();
        } else if (binding.action() == MouseAction.INTERACT) {
            var cab = occupiedCab();
            if (cab == null) return;
            var ray = consoleRay();
            var control = CabPresentation.hit(cab, ray.origin, ray.direction);
            if (control instanceof CabControl.Handle handle)
                drag = new CabHandleDrag(Minecraft.getInstance(), cab, handle, event.getButton(), pointer);
            else if (control instanceof CabControl.Button button)
                HexieNetworkHandler.sendToServer(new CabControlPacket(cab.seatAccess().vehicle().getId(), cab.console(), button.id(), 0));
            else NeoForge.EVENT_BUS.post(ray);
        }
    }

    private static void endDrag(Minecraft mc) {
        if (pointer) drag.restorePointer(mc.getWindow());
        else {
            // Flush the ungrabbed cursor movement before Minecraft resumes mouse look.
            mc.mouseHandler.handleAccumulatedMovement();
            mc.mouseHandler.grabMouse();
        }
        drag = null;
    }

    @SubscribeEvent
    public static void openingScreen(ScreenEvent.Opening event) {
        drag = null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!capturesInput() || !scrollControlsZoom()) return;
        event.setCanceled(true);
        if (drag == null) zoom = Math.clamp(zoom * Math.pow(1.12, event.getScrollDeltaY()), .5, 4);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event) {
        if (context == null || !event.usedConfiguredFov()) return;
        double activeZoom = scrollControlsZoom() ? zoom : 1;
        renderedFov = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(event.getFOV()) / 2) / activeZoom));
        event.setFOV(renderedFov);
    }

    @SubscribeEvent
    public static void hud(RenderGuiLayerEvent.Pre event) {
        var cab = occupiedCab();
        if (cab == null) return;
        var layer = event.getName();
        if (layer.equals(VanillaGuiLayers.HOTBAR) || layer.equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
            if (!cab.simple()) event.setCanceled(true);
        } else if (layer.equals(VanillaGuiLayers.CROSSHAIR) && (pointer || drag != null)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void handleHud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        var cab = occupiedCab();
        if (!capturesInput() || mc.options.hideGui || cab == null) return;
        if (drag != null) {
            if (drag.fits(cab, mc.getWindow())) drag.render(mc, event.getGuiGraphics());
        } else {
            var ray = consoleRay();
            var control = CabPresentation.hit(cab, ray.origin, ray.direction);
            if (control != null) CabControlCaption.hover(mc, event.getGuiGraphics(), control, cab.surfaceState(), pointer);
        }
    }

    private static DrivingConsoleClickEvent consoleRay() {
        var mc = Minecraft.getInstance();
        var camera = mc.gameRenderer.getMainCamera();
        var window = mc.getWindow();
        double x = pointer ? 2 * mc.mouseHandler.xpos() / window.getScreenWidth() - 1 : 0;
        double y = pointer ? 1 - 2 * mc.mouseHandler.ypos() / window.getScreenHeight() : 0;
        double scale = Math.tan(Math.toRadians(renderedFov) / 2);
        var ray = new org.joml.Vector3f((float) (x * scale * window.getWidth() / window.getHeight()),
                (float) (y * scale), -1).rotate(camera.rotation()).normalize();
        Vec3 origin = camera.getPosition(), end = origin.add(ray.x, ray.y, ray.z);
        origin = context.toConsoleLocal(origin, camera.getPartialTickTime());
        end = context.toConsoleLocal(end, camera.getPartialTickTime());
        return new DrivingConsoleClickEvent(context, origin, end.subtract(origin).normalize());
    }
}
