package willow.train.hexie.client.events;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.blocks.locos.DrivingConsoleContext;

@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class DrivingConsoleClient {
    private static DrivingConsoleContext context;
    private static boolean pointer;
    private static boolean exitKeyHeld;
    private static double zoom = 1, renderedFov = 70;

    public static DrivingConsoleContext context() { return context; }

    public static boolean capturesInput() {
        var mc = Minecraft.getInstance();
        return context != null && mc.screen == null && mc.getOverlay() == null && mc.isWindowActive();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        var mc = Minecraft.getInstance();
        var next = DrivingConsoleContext.find(mc.player);
        if (!java.util.Objects.equals(context, next)) {
            boolean wasActive = context != null;
            context = next; pointer = false; zoom = 1;
            if (wasActive || next != null) {
                KeyMapping.releaseAll();
                if (mc.screen == null && mc.isWindowActive() && mc.player != null) mc.mouseHandler.grabMouse();
            }
        }
        if (capturesInput()) {
            mc.options.keyShift.setDown(false);
            mc.player.input.shiftKeyDown = false;
            if (pointer && mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
            if (!pointer && !mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
        }
    }

    /** Preserve pause; the cab owns all other in-game keyboard actions. */
    public static boolean captureKey(int key, int scanCode) {
        return capturesInput() && key != GLFW.GLFW_KEY_ESCAPE;
    }

    public static boolean handleKey(int key, int scanCode, int action) {
        // Consume repeats and release even after the server dismounts us.
        if (key == GLFW.GLFW_KEY_E && exitKeyHeld) {
            if (action == GLFW.GLFW_RELEASE) exitKeyHeld = false;
            return true;
        }
        if (!captureKey(key, scanCode)) return false;
        if (key == GLFW.GLFW_KEY_E && action == GLFW.GLFW_PRESS) {
            var player = Minecraft.getInstance().player;
            if (player != null && player.getVehicle() != null) {
                exitKeyHeld = true;
                willow.train.hexie.network.HexieNetworkHandler.sendToServer(
                        new willow.train.hexie.network.c2s.DrivingConsoleExitPacket(player.getVehicle().getId()));
            }
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (!capturesInput()) return;
        event.setCanceled(true);
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            pointer = !pointer;
            if (pointer) Minecraft.getInstance().mouseHandler.releaseMouse();
            else Minecraft.getInstance().mouseHandler.grabMouse();
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) click();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!capturesInput()) return;
        event.setCanceled(true);
        zoom = Math.clamp(zoom * Math.pow(1.12, event.getScrollDeltaY()), .5, 4);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event) {
        if (context == null || !event.usedConfiguredFov()) return;
        renderedFov = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(event.getFOV()) / 2) / zoom));
        event.setFOV(renderedFov);
    }

    @SubscribeEvent
    public static void hud(RenderGuiLayerEvent.Pre event) {
        if (context == null) return;
        if (event.getName().equals(VanillaGuiLayers.HOTBAR)
                || event.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME)
                || pointer && event.getName().equals(VanillaGuiLayers.CROSSHAIR)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!capturesInput()) return;
        event.setCanceled(true); event.setSwingHand(false);
    }

    private static void click() {
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
        NeoForge.EVENT_BUS.post(new DrivingConsoleClickEvent(context,
                origin, end.subtract(origin).normalize()));
    }
}
