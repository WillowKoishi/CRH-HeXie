package willow.train.hexie.client.interaction;

import java.util.Objects;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.Main;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.api.interaction.ControlSurface;

/** Shared button dispatch and handle gesture ownership for any declared block surface. */
@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class SurfaceInteraction {
    public interface Context {
        Object identity();
        ControlState state();
        BlockControl control(String id);
        boolean valid();
        void submit(BlockControl control, int value);
        default void begin(BlockControl.Handle handle) {}
        default void end(BlockControl.Handle handle) {}
    }

    private static SurfaceHandleDrag drag;

    private SurfaceInteraction() {}

    /** Buttons also accept keyboard use; a handle requires the held physical mouse button. */
    public static boolean click(Context context, ControlSurface.Pick hit, int mouseButton, boolean pointer) {
        if (context == null || hit == null || drag != null || !context.valid()) return false;
        var control = context.control(hit.control().id());
        if (control instanceof BlockControl.Handle handle) {
            if (!SurfaceHandleDrag.canStart(handle)) return false;
            if (mouseButton < 0 || mouseButton > GLFW.GLFW_MOUSE_BUTTON_LAST
                    || GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), mouseButton) != GLFW.GLFW_PRESS)
                return false;
            context.begin(handle);
            try {
                drag = new SurfaceHandleDrag(Minecraft.getInstance(), context, handle, mouseButton, pointer);
            } catch (RuntimeException exception) {
                context.end(handle);
                throw exception;
            }
            KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(mouseButton), false);
            return true;
        }
        if (control instanceof BlockControl.Button && control.accepts(0)) {
            context.submit(control, 0);
            return true;
        }
        return false;
    }

    public static boolean dragging() { return drag != null; }
    public static boolean dragging(Object identity) {
        return drag != null && Objects.equals(drag.identity, identity);
    }

    public static void cancel(Object identity) {
        if (dragging(identity)) endDrag(Minecraft.getInstance());
    }

    public static void hover(ControlSurface.Pick hit, ControlState state, GuiGraphics graphics, boolean pointer) {
        if (drag == null && hit != null && hit.control().showTooltip())
            SurfaceControlCaption.hover(Minecraft.getInstance(), graphics, hit.control(), state, pointer);
    }

    /** Also callable by a surface's input owner so event listener order cannot lose the release. */
    public static boolean handleMouseButton(InputEvent.MouseButton.Pre event) {
        if (drag == null || event.getButton() != drag.button) return false;
        event.setCanceled(true);
        KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), false);
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            var mc = Minecraft.getInstance();
            if (gameplay(mc) && drag.fits(mc.getWindow())) drag.update(mc.getWindow());
            endDrag(mc);
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        handleMouseButton(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (drag == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre event) {
        if (drag == null) return;
        var mc = Minecraft.getInstance();
        if (!gameplay(mc) || !drag.fits(mc.getWindow())
                || GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), drag.button) != GLFW.GLFW_PRESS)
            endDrag(mc);
        else drag.update(mc.getWindow());
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (drag == null) return;
        var mc = Minecraft.getInstance();
        if (gameplay(mc) && !mc.options.hideGui && drag.fits(mc.getWindow()))
            drag.render(mc, event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void crosshair(RenderGuiLayerEvent.Pre event) {
        if (drag != null && event.getName().equals(VanillaGuiLayers.CROSSHAIR)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void openingScreen(ScreenEvent.Opening event) {
        var previous = drag;
        drag = null;
        if (previous != null) previous.end();
    }

    private static boolean gameplay(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.getConnection() != null && mc.player.isAlive()
                && mc.screen == null && mc.getOverlay() == null && mc.isWindowActive();
    }

    private static void endDrag(Minecraft mc) {
        var previous = drag;
        if (previous == null) return;
        try {
            if (!gameplay(mc)) return;
            if (previous.pointer) previous.restorePointer(mc.getWindow());
            else {
                // Keep the gesture visible to the input owner while discarding accumulated mouse look.
                mc.mouseHandler.handleAccumulatedMovement();
                mc.mouseHandler.grabMouse();
            }
        } finally {
            try { previous.end(); }
            finally { if (drag == previous) drag = null; }
        }
    }
}
