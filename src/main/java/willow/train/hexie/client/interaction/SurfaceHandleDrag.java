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
    private final int trackCross;
    private final double originalX, originalY;
    private final double[] mouseX = new double[1], mouseY = new double[1];
    private int notch;
    private boolean ended;
    private long lastSentTick = Long.MIN_VALUE;

    static boolean canStart(BlockControl.Handle handle) {
        return !CabSliderTrack.interactiveNotches(handle).isEmpty();
    }

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
        boolean horizontal = handle.dragSpec().axis() == BlockControl.Handle.DragAxis.HORIZONTAL;
        track = CabSliderTrack.at(handle, notch,
                horizontal ? caption.x() + caption.width() / 2d : caption.y() + caption.height() / 2d,
                horizontal ? guiWidth : guiHeight);
        if (!track.notches().contains(notch)) notch = track.notches().getFirst();
        if (horizontal) {
            int below = caption.y() + caption.height() + 14;
            int margin = Math.min(8, guiHeight / 2);
            trackCross = Math.clamp(below < guiHeight - margin ? below : caption.y() - 14,
                    margin, Math.max(margin, guiHeight - 1 - margin));
        } else trackCross = caption.trackX();
        mc.mouseHandler.releaseMouse();
        // Shift the thumb and cursor together when the full track would extend beyond the screen.
        double x = horizontal ? track.coordinate(notch) : trackCross;
        double y = horizontal ? trackCross : track.coordinate(notch);
        GLFW.glfwSetCursorPos(window.getWindow(), x * window.getScreenWidth() / guiWidth,
                y * window.getScreenHeight() / guiHeight);
    }

    boolean fits(Window window) {
        if (!context.valid() || guiWidth != window.getGuiScaledWidth() || guiHeight != window.getGuiScaledHeight())
            return false;
        return context.control(handle.id()) instanceof BlockControl.Handle current
                && current.accepts(notch) && current.dragSpec().equals(handle.dragSpec());
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
        double coordinate = track.axis() == BlockControl.Handle.DragAxis.HORIZONTAL
                ? mouseX[0] * guiWidth / window.getScreenWidth()
                : mouseY[0] * guiHeight / window.getScreenHeight();
        int next = track.nearest(coordinate);
        var level = Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        if (next == notch && lastSentTick != Long.MIN_VALUE && now - lastSentTick < 10) return;
        notch = next;
        lastSentTick = now;
        context.submit(handle, notch);
    }

    void render(Minecraft mc, GuiGraphics graphics) {
        update(mc.getWindow());
        renderTrack(graphics, trackCross, track, notch);
        if (handle.showTooltip())
            caption.render(mc.font, graphics, SurfaceControlCaption.label(handle, handle.notchText(notch)));
    }

    static void renderTrack(GuiGraphics graphics, int cross, CabSliderTrack track, int notch) {
        var pose = graphics.pose();
        int bottom = track.top() + track.travel();
        int thumb = (int) Math.round(track.coordinate(notch));
        boolean horizontal = track.axis() == BlockControl.Handle.DragAxis.HORIZONTAL;
        pose.pushPose();
        if (horizontal) pose.translate(track.top() - 4, cross - 6, 0);
        else {
            pose.translate(cross - 6, bottom + 4, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(-90));
        }
        graphics.blitSprite(TRACK, 0, 0, track.travel() + 8, 12);
        graphics.blitSprite(THUMB, horizontal ? thumb - track.top() : bottom - thumb, 0, 8, 12);
        pose.popPose();
        for (int value : track.notches()) {
            int point = (int) Math.round(track.coordinate(value));
            int color = value == notch ? 0xFFFFFFFF : 0xFF9A9A9A;
            if (horizontal) graphics.fill(point, cross - 6, point + 1, cross - 3, color);
            else graphics.fill(cross - 6, point, cross - 3, point + 1, color);
        }
    }
}
