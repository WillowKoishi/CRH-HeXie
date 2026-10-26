package willow.train.hexie.client.screens;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;

import java.util.function.Consumer;

public class AdjustableSemaphoreSettingsScreen extends CenteredSemaphoreValueSettingsScreen {
    private final boolean trackAligned;

    public AdjustableSemaphoreSettingsScreen(BlockPos pos, ValueSettingsBoard board, ValueSettings settings,
                                             Consumer<ValueSettings> onHover, int netId, boolean trackAligned) {
        super(pos, board, settings, onHover, netId);
        this.trackAligned = trackAligned;
    }

    @Override
    public ValueSettings getClosestCoordinate(int mouseX, int mouseY) {
        ValueSettings settings = super.getClosestCoordinate(mouseX, mouseY);
        if (settings.row() == 1)
            return new ValueSettings(1, settings.value() > 16 ? 32 : 0);
        return new ValueSettings(0, Math.round(settings.value() / 16f) * 16);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);
        ValueSettings hovered = getClosestCoordinate(mouseX, mouseY);
        for (int value : new int[] {0, 32}) {
            if (hovered.row() == 1 && hovered.value() == value)
                continue;
            Vec2 coordinate = getCoordinateOfValue(1, value);
            Component option = Component.translatable("gui.hexie.semaphore.alignment."
                    + (value == 0 ? "world" : "track"));
            graphics.drawString(font, option, (int) coordinate.x - font.width(option) / 2,
                    (int) coordinate.y - 4, 0x442000, false);
        }
        Component alignment = Component.translatable("gui.hexie.semaphore.current_alignment",
                Component.translatable("gui.hexie.semaphore.alignment." + (trackAligned ? "track" : "world")));
        graphics.drawCenteredString(font, alignment, width / 2, guiTop - 32, 0xFBDC7D);
    }
}
