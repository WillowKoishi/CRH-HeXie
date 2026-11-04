package willow.train.hexie.client.events;

import com.mojang.datafixers.util.Either;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import org.joml.Matrix4f;
import willow.train.hexie.vehicle.control.Crh2CabHandle;

/** Fixed native tooltip bounds. Only the text changes while a handle is held. */
public final class Crh2CabCaption {
    private static final int GAP = 14;

    record Box(int x, int y, int width, int height) {
        int trackX() { return x - GAP; }

        void render(Font font, GuiGraphics graphics, Crh2CabHandle handle, int notch) {
            var row = Crh2CabDrag.label(handle, notch);
            var lines = font.width(row) <= width ? List.of(row.getVisualOrderText()) : font.split(row, width);
            // The vanilla positioner offsets these pointer coordinates by (+12, -12).
            graphics.renderComponentTooltipFromElements(font, List.of(Either.right(new Row(lines, width, height))),
                    x - 12, y + 12, ItemStack.EMPTY);
        }
    }

    static Box at(Font font, Crh2CabHandle handle, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        int leftMargin = Math.min(GAP + 8, screenWidth / 2);
        int available = Math.max(1, screenWidth - leftMargin - 4);
        int width = 1, lines = 1;
        for (int n = handle.minNotch(); n <= handle.maxNotch(); n++) {
            var row = Crh2CabDrag.label(handle, n);
            int natural = font.width(row);
            width = Math.max(width, Math.min(natural, available));
            if (natural > available) lines = Math.max(lines, font.split(row, available).size());
        }
        return position(screenWidth, screenHeight, mouseX, mouseY, width, lines * 10 - 2);
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
