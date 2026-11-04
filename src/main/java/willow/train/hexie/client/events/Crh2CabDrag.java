package willow.train.hexie.client.events;

import com.mojang.blaze3d.platform.Window;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.blocks.emu.DrivingConsoleContext;
import willow.train.hexie.client.cab.Crh2CabSliderTrack;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.Crh2CabHandlePacket;
import willow.train.hexie.vehicle.control.Crh2CabHandle;

/** A held physical handle. Its opening position is always the notch already shown on the desk. */
final class Crh2CabDrag {
    private static final ResourceLocation TRACK = ResourceLocation.withDefaultNamespace("widget/slider");
    private static final ResourceLocation THUMB = ResourceLocation.withDefaultNamespace("widget/slider_handle_highlighted");
    final int button;
    private final Crh2CabHandle handle;
    private final int vehicleId, guiWidth, guiHeight;
    private final Crh2CabCaption.Box caption;
    private final Crh2CabSliderTrack track;
    private final double originalX, originalY;
    private final double[] mouseX = new double[1], mouseY = new double[1];
    private int notch;

    Crh2CabDrag(Minecraft mc, DrivingConsoleContext cab, Crh2CabHandle handle, int button, boolean pointer) {
        this.button = button;
        this.handle = handle;
        vehicleId = cab.seatAccess().vehicle().getId();
        notch = handle.notch(cab.seatAccess().cabControls(cab.console()));
        var window = mc.getWindow();
        guiWidth = window.getGuiScaledWidth();
        guiHeight = window.getGuiScaledHeight();
        originalX = pointer ? mc.mouseHandler.xpos() / window.getScreenWidth() : .5;
        originalY = pointer ? mc.mouseHandler.ypos() / window.getScreenHeight() : .5;
        caption = Crh2CabCaption.at(mc.font, handle, guiWidth, guiHeight, (int) (originalX * guiWidth), (int) (originalY * guiHeight));
        track = Crh2CabSliderTrack.at(handle, notch, caption.y() + caption.height() / 2d, guiHeight);
        mc.mouseHandler.releaseMouse();
        // Shift the thumb and cursor together when the full track would extend beyond the screen.
        GLFW.glfwSetCursorPos(window.getWindow(), caption.trackX() * (double) window.getScreenWidth() / guiWidth,
                track.y(notch) * window.getScreenHeight() / guiHeight);
    }

    boolean fits(Window window) {
        return guiWidth == window.getGuiScaledWidth() && guiHeight == window.getGuiScaledHeight();
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
        HexieNetworkHandler.sendToServer(new Crh2CabHandlePacket(vehicleId, handle, notch));
    }

    void render(Minecraft mc, GuiGraphics graphics) {
        update(mc.getWindow());
        renderTrack(graphics, caption.trackX(), track, notch);
        caption.render(mc.font, graphics, handle, notch);
    }

    static void renderTrack(GuiGraphics graphics, int x, Crh2CabSliderTrack track, int notch) {
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

    static void tooltip(Minecraft mc, GuiGraphics graphics, DrivingConsoleContext cab, Crh2CabHandle handle, boolean pointer) {
        var window = mc.getWindow();
        int x = pointer ? (int) (mc.mouseHandler.xpos() / window.getScreenWidth() * graphics.guiWidth()) : graphics.guiWidth() / 2;
        int y = pointer ? (int) (mc.mouseHandler.ypos() / window.getScreenHeight() * graphics.guiHeight()) : graphics.guiHeight() / 2;
        int notch = handle.notch(cab.seatAccess().cabControls(cab.console()));
        Crh2CabCaption.at(mc.font, handle, graphics.guiWidth(), graphics.guiHeight(), x, y).render(mc.font, graphics, handle, notch);
    }

    static Component label(Crh2CabHandle handle, int notch) {
        var purpose = Component.translatable(switch (handle) {
            case POWER -> "gui.hexie.cab.power";
            case BRAKE -> "gui.hexie.cab.brake";
            case REVERSER -> "gui.hexie.cab.reverser";
        });
        var value = switch (handle) {
            case POWER -> notch == 0 ? Component.translatable("gui.hexie.cab.cut")
                    : Component.translatable("gui.hexie.cab.power_notch", notch);
            case BRAKE -> switch (notch) {
                case 0 -> Component.translatable("gui.hexie.cab.running");
                case 8 -> Component.translatable("gui.hexie.cab.rapid");
                case 9 -> Component.translatable("gui.hexie.cab.remove");
                default -> Component.translatable("gui.hexie.cab.brake_notch", notch);
            };
            case REVERSER -> Component.translatable(switch (notch) {
                case -1 -> "gui.hexie.cab.reverse";
                case 0 -> "gui.hexie.cab.off";
                default -> "gui.hexie.cab.forward";
            });
        };
        return purpose.withStyle(ChatFormatting.GRAY).append("    ")
                .append(value.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }
}
