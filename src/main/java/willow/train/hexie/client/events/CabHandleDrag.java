package willow.train.hexie.client.events;

import com.mojang.blaze3d.platform.Window;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.api.cab.CabControl;
import willow.train.hexie.api.cab.CabSurfaceHost;
import willow.train.hexie.api.cab.DrivingConsoleContext;
import willow.train.hexie.client.cab.CabSliderTrack;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.CabControlPacket;

/** A held physical handle. Its opening position is always the notch already shown on the desk. */
final class CabHandleDrag {
    private static final ResourceLocation TRACK = ResourceLocation.withDefaultNamespace("widget/slider");
    private static final ResourceLocation THUMB = ResourceLocation.withDefaultNamespace("widget/slider_handle_highlighted");
    final int button;
    private final DrivingConsoleContext cab;
    private final Block block;
    private final CabControl.Handle handle;
    private final int guiWidth, guiHeight;
    private final CabControlCaption.Box caption;
    private final CabSliderTrack track;
    private final double originalX, originalY;
    private final double[] mouseX = new double[1], mouseY = new double[1];
    private int notch;

    CabHandleDrag(Minecraft mc, DrivingConsoleContext cab, CabControl.Handle handle, int button, boolean pointer) {
        this.button = button;
        this.cab = cab;
        this.block = cab.seatAccess().blockState(cab.console()).getBlock();
        this.handle = handle;
        notch = handle.notch(cab.surfaceState());
        var window = mc.getWindow();
        guiWidth = window.getGuiScaledWidth();
        guiHeight = window.getGuiScaledHeight();
        originalX = pointer ? mc.mouseHandler.xpos() / window.getScreenWidth() : .5;
        originalY = pointer ? mc.mouseHandler.ypos() / window.getScreenHeight() : .5;
        caption = CabControlCaption.at(mc.font, handle, guiWidth, guiHeight, (int) (originalX * guiWidth), (int) (originalY * guiHeight));
        track = CabSliderTrack.at(handle, notch, caption.y() + caption.height() / 2d, guiHeight);
        mc.mouseHandler.releaseMouse();
        // Shift the thumb and cursor together when the full track would extend beyond the screen.
        GLFW.glfwSetCursorPos(window.getWindow(), caption.trackX() * (double) window.getScreenWidth() / guiWidth,
                track.y(notch) * window.getScreenHeight() / guiHeight);
    }

    boolean fits(DrivingConsoleContext current, Window window) {
        if (!cab.equals(current) || guiWidth != window.getGuiScaledWidth() || guiHeight != window.getGuiScaledHeight())
            return false;
        var state = current.seatAccess().blockState(current.console());
        if (state == null || !state.is(block) || !(block instanceof CabSurfaceHost host)) return false;
        for (var control : host.controls(state))
            if (control instanceof CabControl.Handle && control.id().equals(handle.id())) return true;
        return false;
    }

    void restorePointer(Window window) {
        GLFW.glfwSetCursorPos(window.getWindow(), originalX * window.getScreenWidth(), originalY * window.getScreenHeight());
    }

    void update(Window window) {
        GLFW.glfwGetCursorPos(window.getWindow(), mouseX, mouseY);
        double y = mouseY[0] * guiHeight / window.getScreenHeight();
        int next = track.nearest(y);
        if (next == notch) return;
        notch = next;
        HexieNetworkHandler.sendToServer(new CabControlPacket(cab.seatAccess().vehicle().getId(), cab.console(), handle.id(), notch));
    }

    void render(Minecraft mc, GuiGraphics graphics) {
        update(mc.getWindow());
        renderTrack(graphics, caption.trackX(), track, notch);
        caption.render(mc.font, graphics, CabControlCaption.label(handle, handle.notchText(notch)));
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
