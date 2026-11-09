package willow.train.hexie.client.interaction;

import com.mojang.datafixers.util.Either;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import org.joml.Matrix4f;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.client.cab.CabSliderTrack;

/** Fixed native tooltip bounds. Only the text changes while a handle is held. */
public final class SurfaceControlCaption {
    private static final int GAP = 14;

    record Box(int x, int y, int width, int height) {
        int trackX() { return x - GAP; }

        void render(Font font, GuiGraphics graphics, Component row) {
            var lines = font.width(row) <= width ? List.of(row.getVisualOrderText()) : font.split(row, width);
            // The vanilla positioner offsets these pointer coordinates by (+12, -12).
            graphics.renderComponentTooltipFromElements(font, List.of(Either.right(new Row(lines, width, height))),
                    x - 12, y + 12, ItemStack.EMPTY);
        }
    }

    static Box at(Font font, BlockControl.Handle handle, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        int leftMargin = Math.min(GAP + 8, screenWidth / 2);
        int available = Math.max(1, screenWidth - leftMargin - 4);
        var title = label(handle, Component.empty());
        int width = Math.max(1, Math.min(font.width(title), available));
        int lines = Math.max(1, font.split(title, available).size());
        for (int n : CabSliderTrack.interactiveNotches(handle)) {
            var row = label(handle, handle.notchText(n));
            int natural = font.width(row);
            width = Math.max(width, Math.min(natural, available));
            if (natural > available) lines = Math.max(lines, font.split(row, available).size());
        }
        return position(screenWidth, screenHeight, mouseX, mouseY, width, lines * 10 - 2);
    }

    static Component label(BlockControl control, Component value) {
        var name = control.nameText().copy().withStyle(ChatFormatting.GRAY);
        if (value.getString().isEmpty()) return name;
        return name.append("    ").append(value.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }

    static void hover(Minecraft mc, GuiGraphics graphics, BlockControl control, ControlState state, boolean pointer) {
        var window = mc.getWindow();
        int x = pointer ? (int) (mc.mouseHandler.xpos() / window.getScreenWidth() * graphics.guiWidth()) : graphics.guiWidth() / 2;
        int y = pointer ? (int) (mc.mouseHandler.ypos() / window.getScreenHeight() * graphics.guiHeight()) : graphics.guiHeight() / 2;
        var row = control instanceof BlockControl.Handle handle && CabSliderTrack.interactiveNotches(handle).isEmpty()
                ? label(control, Component.empty()) : label(control, control.valueText(state));
        if (control instanceof BlockControl.Handle handle)
            at(mc.font, handle, graphics.guiWidth(), graphics.guiHeight(), x, y).render(mc.font, graphics, row);
        else graphics.renderTooltip(mc.font, row, x, y);
    }

    /** Held keyboard gestures use the same native labels as a mouse-operated handle. */
    static void held(Minecraft mc, GuiGraphics graphics, List<BlockControl> controls, ControlState state) {
        var rows = controls.stream().map(control -> label(control, control.valueText(state))).toList();
        graphics.renderComponentTooltip(mc.font, rows, graphics.guiWidth() / 2, graphics.guiHeight() / 2);
    }

    /** Content, including key labels, comes from the block. An empty declaration has no prompt. */
    static void entry(Minecraft mc, GuiGraphics graphics, List<Component> lines) {
        if (lines.isEmpty()) return;
        var elements = lines.stream().map(line -> Either.<FormattedText, TooltipComponent>left(line)).toList();
        graphics.renderComponentTooltipFromElements(mc.font, elements,
                graphics.guiWidth() / 2, graphics.guiHeight() / 2, ItemStack.EMPTY);
    }

    static Box position(int screenWidth, int screenHeight, int mouseX, int mouseY, int width, int height) {
        int leftMargin = Math.min(GAP + 8, screenWidth / 2);
        var pos = DefaultTooltipPositioner.INSTANCE.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, width, height);
        int x = Math.clamp(pos.x(), leftMargin, Math.max(leftMargin, screenWidth - width - 4));
        int y = Math.clamp(pos.y(), 4, Math.max(4, screenHeight - height - 4));
        return new Box(x, y, width, height);
    }

    private record Row(List<FormattedCharSequence> lines, int width, int height) implements TooltipComponent, ClientTooltipComponent {
        @Override public int getWidth(Font font) { return width; }
        @Override public int getHeight() { return height + 2; }
        @Override public void renderText(Font font, int x, int y, Matrix4f pose, MultiBufferSource.BufferSource buffers) {
            for (var line : lines) {
                new ClientTextTooltip(line).renderText(font, x, y, pose, buffers);
                y += 10;
            }
        }
    }

    public static void register(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(Row.class, row -> row);
    }
}
