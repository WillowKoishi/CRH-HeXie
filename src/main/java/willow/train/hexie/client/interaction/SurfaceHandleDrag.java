package willow.train.hexie.client.interaction;

import com.mojang.blaze3d.platform.Window;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
    private final List<NotchLabel> labels;
    private final float labelScale;
    private final double originalX, originalY;
    private final double[] mouseX = new double[1], mouseY = new double[1];
    private int notch;
    private boolean ended;
    private long lastSentTick = Long.MIN_VALUE;

    private record NotchLabel(int value, Component text, int width) {}

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
        boolean horizontal = handle.dragSpec().axis() == BlockControl.Handle.DragAxis.HORIZONTAL;
        labels = CabSliderTrack.interactiveNotches(handle).stream().map(value -> {
            var text = handle.detentText(value);
            return new NotchLabel(value, text, mc.font.width(text));
        }).toList();
        int maxLabelWidth = labels.stream().mapToInt(NotchLabel::width).max().orElse(0);
        int leftMargin = horizontal ? Math.min(22, guiWidth / 2)
                : Math.max(Math.min(22, guiWidth / 2), Math.min(maxLabelWidth + 28, guiWidth * 2 / 5));
        caption = SurfaceControlCaption.at(mc.font, handle, guiWidth, guiHeight,
                (int) (originalX * guiWidth), (int) (originalY * guiHeight), leftMargin);
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
        labelScale = labelScale(mc.font, track, labels, trackCross, guiWidth);
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
        if (handle.showDetentLabels()) renderLabels(mc.font, graphics);
        if (handle.showTooltip())
            caption.render(mc.font, graphics, SurfaceControlCaption.label(handle, handle.notchText(notch)));
    }

    private static float labelScale(Font font, CabSliderTrack track, List<NotchLabel> labels, int cross, int width) {
        double[] coordinates = labels.stream().mapToDouble(label -> track.coordinate(label.value())).sorted().toArray();
        double smallestGap = Double.POSITIVE_INFINITY;
        for (int i = 1; i < coordinates.length; i++)
            smallestGap = Math.min(smallestGap, coordinates[i] - coordinates[i - 1]);
        int maxWidth = labels.stream().mapToInt(NotchLabel::width).max().orElse(0);
        boolean horizontal = track.axis() == BlockControl.Handle.DragAxis.HORIZONTAL;
        double availableWidth = horizontal ? Math.max(1, width - 4) : Math.max(1, cross - 14);
        double scale = maxWidth == 0 ? 1 : Math.min(1, availableWidth / maxWidth);
        if (Double.isFinite(smallestGap)) {
            int naturalSize = horizontal ? Math.max(1, maxWidth) : font.lineHeight;
            scale = Math.min(scale, Math.max(.01, smallestGap - 1) / (naturalSize + 1));
        }
        return (float) Math.max(.01, scale);
    }

    private void renderLabels(Font font, GuiGraphics graphics) {
        boolean horizontal = track.axis() == BlockControl.Handle.DragAxis.HORIZONTAL;
        float height = font.lineHeight * labelScale;
        for (var label : labels) {
            float point = (float) track.coordinate(label.value());
            float textWidth = label.width() * labelScale;
            float x = horizontal ? Math.clamp(point - textWidth / 2, 2, Math.max(2, guiWidth - textWidth - 2))
                    : trackCross - 10 - textWidth;
            float y = horizontal ? trackCross + 9 : point - height / 2;
            if (horizontal && y + height > guiHeight - 2) y = trackCross - 9 - height;
            graphics.fill((int) Math.floor(x - 2), (int) Math.floor(y - 1),
                    (int) Math.ceil(x + textWidth + 2), (int) Math.ceil(y + height + 1), 0xA0000000);
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(labelScale, labelScale, 1);
            graphics.drawString(font, label.text(), 0, 0, label.value() == notch ? 0xFFFFFF55 : 0xFFE0E0E0, true);
            pose.popPose();
        }
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
