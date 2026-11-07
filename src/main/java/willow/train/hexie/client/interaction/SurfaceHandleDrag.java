package willow.train.hexie.client.interaction;

import com.mojang.blaze3d.platform.Window;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.client.cab.CabSliderTrack;

/** A held physical handle. The target adapter owns state, validity and command delivery. */
final class SurfaceHandleDrag {
    private static final ResourceLocation TRACK = ResourceLocation.withDefaultNamespace("widget/slider");
    private static final ResourceLocation THUMB = ResourceLocation.withDefaultNamespace("widget/slider_handle_highlighted");
    final int button;
    private final SurfaceInteraction.Context context;
    final Object identity;
    final boolean pointer;
    private final BlockControl.Handle handle;
    private final int guiWidth, guiHeight;
    private final SurfaceControlCaption.Box caption;
    private final CabSliderTrack track;
    private final double originalX, originalY;
    private final double[] mouseX = new double[1], mouseY = new double[1];
    private int notch;
    private boolean ended;

    SurfaceHandleDrag(Minecraft mc, SurfaceInteraction.Context context, BlockControl.Handle handle, int button, boolean pointer) {
        this.button = button;
        this.context = context;
        this.identity = context.identity();
        this.pointer = pointer;
        this.handle = handle;
        notch = handle.notch(context.state());
        var window = mc.getWindow();
        guiWidth = window.getGuiScaledWidth();
        guiHeight = window.getGuiScaledHeight();
        originalX = pointer ? mc.mouseHandler.xpos() / window.getScreenWidth() : .5;
        originalY = pointer ? mc.mouseHandler.ypos() / window.getScreenHeight() : .5;
        caption = SurfaceControlCaption.at(mc.font, handle, guiWidth, guiHeight, (int) (originalX * guiWidth), (int) (originalY * guiHeight));
        track = CabSliderTrack.at(handle, notch, caption.y() + caption.height() / 2d, guiHeight);
        mc.mouseHandler.releaseMouse();
        // Shift the thumb and cursor together when the full track would extend beyond the screen.
        GLFW.glfwSetCursorPos(window.getWindow(), caption.trackX() * (double) window.getScreenWidth() / guiWidth,
                track.y(notch) * window.getScreenHeight() / guiHeight);
    }

    boolean fits(Window window) {
        if (!context.valid() || guiWidth != window.getGuiScaledWidth() || guiHeight != window.getGuiScaledHeight())
            return false;
        return context.control(handle.id()) instanceof BlockControl.Handle;
    }

    void restorePointer(Window window) {
        GLFW.glfwSetCursorPos(window.getWindow(), originalX * window.getScreenWidth(), originalY * window.getScreenHeight());
    }

    void end() {
        if (ended) return;
        ended = true;
        context.end(handle);
    }

    void update(Window window) {
        GLFW.glfwGetCursorPos(window.getWindow(), mouseX, mouseY);
        double y = mouseY[0] * guiHeight / window.getScreenHeight();
        int next = track.nearest(y);
        if (next == notch) return;
        notch = next;
        context.submit(handle, notch);
    }

    void render(Minecraft mc, GuiGraphics graphics) {
        update(mc.getWindow());
        renderTrack(graphics, caption.trackX(), track, notch);
        caption.render(mc.font, graphics, SurfaceControlCaption.label(handle, handle.notchText(notch)));
    }

    static void renderTrack(GuiGraphics graphics, int x, CabSliderTrack track, int notch) {
        var pose = graphics.pose();
        int bottom = track.top() + track.travel();
        int thumb = (int) Math.round(track.y(notch));
        pose.pushPose();
        pose.translate(x - 6, bottom + 4, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-90));
        graphics.blitSprite(TRACK, 0, 0, track.travel() + 8, 12);
        graphics.blitSprite(THUMB, bottom - thumb, 0, 8, 12);
        pose.popPose();
        for (int i = track.handle().minNotch(); i <= track.handle().maxNotch(); i++) {
            int y = (int) Math.round(track.y(i));
            graphics.fill(x - 6, y, x - 3, y + 1, i == notch ? 0xFFFFFFFF : 0xFF9A9A9A);
        }
    }
}
