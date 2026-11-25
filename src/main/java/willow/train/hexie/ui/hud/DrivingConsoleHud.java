package willow.train.hexie.ui.hud;

// split from willow/train/hexie/client/events/DrivingConsoleHud.java

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.api.cab.DmiState;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;
import willow.train.hexie.ui.events.input.DrivingConsoleClient;

/** Compact driving HUD using the occupied console's live train and DMI readings. */
@EventBusSubscriber(modid = HexieConstants.MOD_ID, value = Dist.CLIENT)
public final class DrivingConsoleHud {
    private static final int SCALE_REFERENCE_WIDTH = 600;
    private static final int HEIGHT = 40;
    private static final int COLUMN_PADDING = 5;
    private static final int ICON_GAP = 4;
    private static final int MAX_ROUTE_TEXT_WIDTH = 140;
    private static boolean visible = true;

    private DrivingConsoleHud() {}

    @SubscribeEvent
    public static void toggle(InputEvent.Key event) {
        if (event.getKey() != GLFW.GLFW_KEY_TAB || event.getAction() != GLFW.GLFW_PRESS) return;
        var mc = Minecraft.getInstance();
        if (mc.screen == null && occupiedCab(mc) != null) visible = !visible;
    }

    /** Tab is reserved for the driving HUD while seated, so the scoreboard does not cover the readouts. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void suppressPlayerList(RenderGuiLayerEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (event.getName().equals(VanillaGuiLayers.TAB_LIST) && mc.screen == null
                && occupiedCab(mc) != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        var cab = occupiedCab(mc);
        if (cab == null || !gameplay(mc) || mc.options.hideGui) return;

        var graphics = event.getGuiGraphics();
        if (visible) renderRibbon(graphics, DrivingConsoleTelemetry.readings(cab), mc.level.getDayTime());
        renderHint(graphics);
    }

    private static DrivingConsoleContext occupiedCab(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null
                || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return null;
        return DrivingConsoleClient.occupiedCab();
    }

    private static boolean gameplay(Minecraft mc) {
        return mc.player != null && mc.player.isAlive() && mc.screen == null && mc.getOverlay() == null;
    }

    private static void renderRibbon(GuiGraphics graphics, DrivingConsoleTelemetry.Readings data, long dayTime) {
        var font = Minecraft.getInstance().font;
        var clock = HudFonts.layout(font, gameClock(dayTime));
        var day = HudFonts.layout(font, localized("day", Math.floorDiv(dayTime, 24000L) + 1));
        var targetLabel = fit(font, data.routeTarget() == null ? localized("status")
                : data.routeTarget().kind() == DmiState.TargetKind.STATION
                ? localized("stop_position") : localized("ahead_target"), MAX_ROUTE_TEXT_WIDTH);
        var routeName = fit(font, data.routeTarget() == null || data.routeTarget().name().isBlank()
                ? data.routeTarget() == null ? localized("train_running") : targetKindName(data.routeTarget().kind())
                : data.routeTarget().name(), MAX_ROUTE_TEXT_WIDTH);
        var routeDistance = data.routeTarget() == null ? HudFonts.layout(font, "")
                : fit(font, localized("distance", distance(data.routeTarget().distanceM())), MAX_ROUTE_TEXT_WIDTH);
        var currentLabel = HudFonts.layout(font, localized("current_speed"));
        var currentText = HudFonts.layout(font, Integer.toString((int) Math.round(data.speedKmh())));
        var targetSpeedLabel = HudFonts.layout(font, localized("target_speed"));
        var targetText = HudFonts.layout(font,
                data.targetKmh() < 0 ? "--" : Integer.toString((int) Math.round(data.targetKmh())));
        var speedUnit = HudFonts.layout(font, localized("unit_kmh"));
        boolean braking = data.brake() > .01;
        var loadLabel = HudFonts.layout(font, localized(braking ? "brake" : "traction"));
        var loadText = HudFonts.layout(font,
                String.format(java.util.Locale.ROOT, "%.0f%%", (braking ? data.brake() : data.traction()) * 100));

        int clockGroupWidth = 14 + ICON_GAP + clock.width();
        int clockWidth = Math.max(clockGroupWidth, day.width()) + COLUMN_PADDING * 2;
        int routeTextWidth = Math.max(targetLabel.width(), Math.max(routeName.width(), routeDistance.width()));
        int routeWidth = 16 + ICON_GAP + routeTextWidth + COLUMN_PADDING * 2;
        float currentGroupWidth = currentText.exactWidth() + ICON_GAP + speedUnit.exactWidth();
        int reservedSpeedWidth = HudFonts.layout(font, "000").width() + ICON_GAP + speedUnit.width();
        int currentWidth = Math.max(currentLabel.width(), Math.max(Mth.ceil(currentGroupWidth), reservedSpeedWidth))
                + COLUMN_PADDING * 2;
        int targetGroupWidth = 25 + ICON_GAP + speedUnit.width();
        int targetWidth = Math.max(targetSpeedLabel.width(), targetGroupWidth) + COLUMN_PADDING * 2;
        int loadTextWidth = Math.max(loadLabel.width(), Math.max(loadText.width(), HudFonts.layout(font, "100%").width()));
        int loadWidth = 14 + ICON_GAP + loadTextWidth + COLUMN_PADDING * 2;

        int routeLeft = clockWidth;
        int currentLeft = routeLeft + routeWidth;
        int targetLeft = currentLeft + currentWidth;
        int loadLeft = targetLeft + targetWidth;
        int width = loadLeft + loadWidth;
        float scale = Math.min(graphics.guiWidth() * .6f / SCALE_REFERENCE_WIDTH,
                (graphics.guiWidth() - 16f) / width);
        if (scale <= 0) return;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate((graphics.guiWidth() - width * scale) / 2, 8, 0);
        pose.scale(scale, scale, 1);

        graphics.fill(0, 0, width, HEIGHT, 0xe70a111a);
        graphics.fill(0, 0, width, 2, 0xff4aa7b6);
        graphics.fill(0, HEIGHT - 2, width, HEIGHT, 0xff263b48);
        graphics.fillGradient(0, 2, width, HEIGHT - 2, 0xe7162029, 0xe8091018);
        for (int x : new int[]{routeLeft, currentLeft, targetLeft, loadLeft})
            graphics.fill(x, 6, x + 1, HEIGHT - 6, 0x664d7c88);

        int clockX = (clockWidth - clockGroupWidth) / 2;
        drawClock(graphics, clockX, 5, 0xffb9e4e8, 0xe7162029);
        clock.draw(graphics, clockX + 14 + ICON_GAP, 8, 0xffeef4f5);
        drawCentered(graphics, day, clockWidth / 2, 24, 0xffaab9c0);

        int routeX = routeLeft + (routeWidth - (16 + ICON_GAP + routeTextWidth)) / 2;
        int routeTextCenter = routeX + 16 + ICON_GAP + routeTextWidth / 2;
        drawTargetMarker(graphics, routeX, 12, data.routeTarget() != null);
        drawCentered(graphics, targetLabel, routeTextCenter, data.routeTarget() == null ? 7 : 3, 0xffffd94a);
        drawCentered(graphics, routeName, routeTextCenter, data.routeTarget() == null ? 22 : 15, 0xffd8e0e3);
        if (data.routeTarget() != null)
            drawCentered(graphics, routeDistance, routeTextCenter, 27, 0xff9eabb1);

        drawCentered(graphics, currentLabel, currentLeft + currentWidth / 2, 6, 0xffaebcc3);
        float currentX = currentLeft + (currentWidth - currentGroupWidth) / 2;
        currentText.draw(graphics, currentX, 23, speedColor(data.speedKmh(), data.targetKmh()));
        speedUnit.draw(graphics, currentX + currentText.exactWidth() + ICON_GAP, 23, 0xffd8e0e3);

        drawCentered(graphics, targetSpeedLabel, targetLeft + targetWidth / 2, 3, 0xffaebcc3);
        int circleX = targetLeft + (targetWidth - targetGroupWidth) / 2 + 12;
        drawCircle(graphics, circleX, 25, 12, 0xff8da7af);
        drawCircle(graphics, circleX, 25, 10, 0xff17232b);
        drawCentered(graphics, targetText, circleX, 21, 0xfff4f7f7);
        speedUnit.draw(graphics, circleX + 13 + ICON_GAP, 21, 0xffd8e0e3);

        int loadColor = braking ? 0xffffc55c : 0xffb9e4e8;
        int loadX = loadLeft + (loadWidth - (14 + ICON_GAP + loadTextWidth)) / 2;
        int loadTextCenter = loadX + 14 + ICON_GAP + loadTextWidth / 2;
        drawLoadIcon(graphics, loadX, 14, braking, loadColor);
        drawCentered(graphics, loadLabel, loadTextCenter, 7, 0xffaebcc3);
        drawCentered(graphics, loadText, loadTextCenter, 23, loadColor);
        pose.popPose();
    }

    private static void drawCentered(GuiGraphics graphics, HudFonts.Text text, int centerX, int y, int color) {
        text.draw(graphics, centerX - text.exactWidth() / 2, y, color);
    }

    private static void renderHint(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        var text = HudFonts.layout(font, localized("tab_hint"));
        int width = text.width() + 14;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 20;
        graphics.fill(x, y, x + width, y + 15, 0xb90a111a);
        graphics.fill(x, y, x + width, y + 1, 0x995b8790);
        text.draw(graphics, x + 7, y + 3, 0xffe1e7e9);
    }

    private static int speedColor(double current, double target) {
        if (target < 0 || current <= target) return 0xfff4f7f7;
        double overspeed = current - target;
        return overspeed <= 5 ? 0xffffd94a : 0xffff4949;
    }

    private static String gameClock(long dayTime) {
        long ticks = Math.floorMod(dayTime, 24000L);
        long seconds = Math.floorMod(ticks + 6000L, 24000L) * 86400L / 24000L;
        long hour = seconds / 3600;
        long minute = seconds / 60 % 60;
        long second = seconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hour, minute, second);
    }

    private static String distance(double metres) {
        return metres >= 1000
                ? localized("distance_km", String.format(java.util.Locale.ROOT, "%.1f", metres / 1000))
                : localized("distance_m", String.format(java.util.Locale.ROOT, "%.0f", metres));
    }

    private static String targetKindName(DmiState.TargetKind kind) {
        return switch (kind) {
            case STATION -> localized("station");
            case SIGNAL -> localized("signal");
            case SPEED_RESTRICTION -> localized("speed_restriction");
        };
    }

    private static String localized(String key, Object... args) {
        return Component.translatable("gui.hexie.driving_hud." + key, args).getString();
    }

    private static HudFonts.Text fit(Font font, String value, int maxWidth) {
        var layout = HudFonts.layout(font, value);
        if (layout.width() <= maxWidth) return layout;
        while (!value.isEmpty()) {
            value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
            layout = HudFonts.layout(font, value + "…");
            if (layout.width() <= maxWidth) return layout;
        }
        return HudFonts.layout(font, "…");
    }

    private static void drawClock(GuiGraphics graphics, int x, int y, int color, int background) {
        drawCircle(graphics, x + 7, y + 7, 7, color);
        drawCircle(graphics, x + 7, y + 7, 5, background);
        graphics.fill(x + 6, y + 3, x + 8, y + 8, color);
        graphics.fill(x + 7, y + 7, x + 11, y + 9, color);
    }

    private static void drawTargetMarker(GuiGraphics graphics, int x, int y, boolean active) {
        int color = active ? 0xffffd94a : 0xff8da7af;
        drawCircle(graphics, x + 8, y + 8, 8, color);
        drawCircle(graphics, x + 8, y + 8, 5, 0xe7162029);
        graphics.fill(x + 6, y + 4, x + 10, y + 10, color);
        graphics.fill(x + 4, y + 9, x + 12, y + 11, color);
        graphics.fill(x + 5, y + 11, x + 7, y + 15, color);
        graphics.fill(x + 9, y + 11, x + 11, y + 15, color);
    }

    private static void drawLoadIcon(GuiGraphics graphics, int x, int y, boolean braking, int color) {
        graphics.fill(x + 2, y, x + 12, y + 3, color);
        graphics.fill(x, y + 3, x + 14, y + 7, color);
        graphics.fill(x + 2, y + 7, x + 12, y + 11, color);
        if (braking) graphics.fill(x + 5, y + 3, x + 9, y + 11, 0xff26313a);
        else {
            graphics.fill(x + 5, y + 3, x + 7, y + 10, 0xff26313a);
            graphics.fill(x + 8, y + 5, x + 10, y + 10, 0xff26313a);
        }
    }

    private static void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.floor(Math.sqrt(radius * radius - dy * dy));
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }
}
