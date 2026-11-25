package willow.train.hexie.interaction.surface;

import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.ControlInputLayout.Action;

// split from willow/train/hexie/client/interaction/SurfaceInputClient.java
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class SurfaceInputView {
    private static double zoom = 1;
    private static double renderedFov = 70;

    static void resetZoom() {
        zoom = 1;
    }

    static void adjustZoom(double steps) {
        zoom = Math.clamp(zoom * Math.pow(1.12, steps), .5, 4);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void arm(RenderArmEvent event) {
        if (SurfaceInputClient.capturesInput() && SurfaceInputClient.active.layout().view().hideHands()
                && event.getPlayer() == Minecraft.getInstance().player) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void hud(RenderGuiLayerEvent.Pre event) {
        if (!SurfaceInputClient.capturesInput()) return;
        var layer = event.getName();
        if (SurfaceInputClient.active.layout().view().hideHotbar() && (layer.equals(VanillaGuiLayers.HOTBAR)
                || layer.equals(VanillaGuiLayers.SELECTED_ITEM_NAME))
                || SurfaceInputClient.pointer && layer.equals(VanillaGuiLayers.CROSSHAIR)) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void hover(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!SurfaceInputClient.gameplay() || mc.options.hideGui || SurfaceInteraction.dragging() || SurfaceInputSources.blocked()) return;
        if (!SurfaceInputClient.capturesInput()) {
            var hit = SurfacePointerQuery.pointed(null);
            if (hit != null) {
                if (hit.control().control().showTooltip())
                    SurfaceInteraction.hover(hit.control(), hit.target().state(), event.getGuiGraphics(), false);
                return;
            }
            var entry = SurfacePointerQuery.pointedEntry();
            if (entry != null) SurfaceControlCaption.entry(mc, event.getGuiGraphics(), entry.entry().hint());
            return;
        }
        var pushed = new LinkedHashMap<String, BlockControl>();
        for (var binding : SurfaceInputClient.active.layout().bindings(SurfaceInputClient.active.cab != null)) {
            if (binding.action() != Action.STEP_HOLD) continue;
            var key = SurfaceInputClient.key(binding);
            if (key == null || !SurfaceInputClient.physicallyDown(key)) continue;
            var control = SurfaceInputClient.active.control(binding.controlId());
            if (control != null && control.showTooltip()) pushed.putIfAbsent(control.id(), control);
        }
        if (!pushed.isEmpty()) {
            SurfaceControlCaption.held(mc, event.getGuiGraphics(), pushed.values().stream().toList(), SurfaceInputClient.active.state());
            return;
        }
        var ray = ray(SurfaceInputClient.pointer);
        var hit = SurfaceInputClient.active.pick(ray.from(), ray.to());
        if (hit != null && hit.control().control().showTooltip())
            SurfaceInteraction.hover(hit.control(), SurfaceInputClient.active.state(), event.getGuiGraphics(), SurfaceInputClient.pointer);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || event.getRenderer().isPanoramicMode()) return;
        double factor = SurfaceInputClient.capturesInput() && SurfaceInputClient.active.layout().view().zoom() ? zoom : 1;
        renderedFov = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(event.getFOV()) / 2) / factor));
        if (factor != 1) event.setFOV(renderedFov);
    }

    record Ray(Vec3 from, Vec3 to) {}
    static Ray ray(boolean pointer) {
        var mc = Minecraft.getInstance();
        var camera = mc.gameRenderer.getMainCamera();
        var window = mc.getWindow();
        double x = pointer ? 2 * mc.mouseHandler.xpos() / window.getScreenWidth() - 1 : 0;
        double y = pointer ? 1 - 2 * mc.mouseHandler.ypos() / window.getScreenHeight() : 0;
        double scale = Math.tan(Math.toRadians(renderedFov) / 2);
        var direction = new org.joml.Vector3f((float) (x * scale * window.getWidth() / window.getHeight()),
                (float) (y * scale), -1).rotate(camera.rotation()).normalize();
        Vec3 eye = camera.getPosition();
        return new Ray(eye, eye.add(direction.x * mc.player.blockInteractionRange(),
                direction.y * mc.player.blockInteractionRange(), direction.z * mc.player.blockInteractionRange()));
    }
}
