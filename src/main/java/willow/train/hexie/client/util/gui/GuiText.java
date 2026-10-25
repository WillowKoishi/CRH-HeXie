package willow.train.hexie.client.util.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class GuiText {
    private GuiText() {}
    public static void draw(GuiGraphics graphics, Font font, Component text, float x, float y, int color) { graphics.drawString(font, text, (int) x, (int) y, color, false); }
    public static void draw(GuiGraphics graphics, Font font, String text, float x, float y, int color) { graphics.drawString(font, text, (int) x, (int) y, color, false); }
    public static void draw(GuiGraphics graphics, Font font, FormattedCharSequence text, float x, float y, int color) { graphics.drawString(font, text, (int) x, (int) y, color, false); }
}
