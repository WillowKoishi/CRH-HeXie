package willow.train.hexie.client.screens.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.widget.Label;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/** Centered, scrolling labels adapted from Simurail's SLabel (bundled MIT notice). */
public final class BogeyMenuLabel extends Label {
    private final int margin;
    private boolean overflowTooltip;
    private boolean clickSound;

    public BogeyMenuLabel(int x, int y, int width, int height, int margin) {
        super(x, y, Component.empty());
        this.width = width;
        this.height = height;
        this.margin = margin;
        text = Component.empty();
        withShadow();
    }

    public BogeyMenuLabel withOverflowTooltip() { overflowTooltip = true; return this; }
    public BogeyMenuLabel withClickSound() { clickSound = true; return this; }
    public void withTooltip(List<Component> tooltip) { overflowTooltip = false; toolTip = tooltip; }

    @Override public List<Component> getToolTip() {
        return overflowTooltip && font.width(text) > width - 2 * margin ? List.of(text) : super.getToolTip();
    }
    @Override public void playDownSound(SoundManager sounds) { if (clickSound) super.playDownSound(sounds); }
    @Override protected void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (text == null || text.getString().isEmpty()) return;
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int minX = getX() + margin;
        int maxX = getX() + width - margin;
        int y = getY() + height / 2 - font.lineHeight / 2;
        int overflow = font.width(text) - (maxX - minX);
        if (overflow <= 0) {
            graphics.drawString(font, text, minX + (maxX - minX) / 2 - font.width(text) / 2, y, color, hasShadow);
            return;
        }
        double time = Util.getMillis() * .001;
        double period = Math.max(overflow * .5, 3);
        double offset = (Math.sin(Math.PI / 2 * Math.cos(2 * Math.PI * time / period)) / 2 + .5) * overflow;
        graphics.flush();
        graphics.enableScissor(minX, y, maxX, y + font.lineHeight);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(-offset, 0, 0);
            graphics.drawString(font, text, minX, y, color, hasShadow);
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }
}
