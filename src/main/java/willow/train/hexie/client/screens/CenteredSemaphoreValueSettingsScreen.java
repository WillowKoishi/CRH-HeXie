package willow.train.hexie.client.screens;

import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;

import java.util.function.Consumer;

/** Positions the cursor over the signal's currently selected Create value. */
public class CenteredSemaphoreValueSettingsScreen extends ValueSettingsScreen {

    private Vec2 startingCursor;
    private boolean cursorMoved;
    private boolean positioningInitialCursor;

    public CenteredSemaphoreValueSettingsScreen(BlockPos pos, ValueSettingsBoard board,
                                                 ValueSettings currentSettings, Consumer<ValueSettings> onHover,
                                                 int netId) {
        super(pos, board, currentSettings, onHover, netId);
    }

    @Override
    protected void init() {
        positioningInitialCursor = true;
        try {
            super.init();
        } finally {
            positioningInitialCursor = false;
        }
    }

    @Override
    public Vec2 getCoordinateOfValue(int row, int column) {
        Vec2 coordinate = super.getCoordinateOfValue(row, column);
        if (!positioningInitialCursor)
            return coordinate;

        startingCursor = coordinate;
        // Create multiplies this coordinate by guiScale before passing it to
        // GLFW. Correct it here so that its first cursor move lands on the
        // selected value, including on high-DPI displays.
        Window window = minecraft.getWindow();
        double guiScale = window.getGuiScale();
        return new Vec2(
                (float) (coordinate.x * window.getScreenWidth() / window.getGuiScaledWidth() / guiScale),
                (float) (coordinate.y * window.getScreenHeight() / window.getGuiScaledHeight() / guiScale));
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindow(graphics, mouseX, mouseY, partialTicks);
        if (startingCursor != null
                && (Math.abs(mouseX - startingCursor.x) > 1 || Math.abs(mouseY - startingCursor.y) > 1))
            cursorMoved = true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        cursorMoved = true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void saveAndClose(double mouseX, double mouseY) {
        if (startingCursor != null
                && (Math.abs(mouseX - startingCursor.x) > 1 || Math.abs(mouseY - startingCursor.y) > 1))
            cursorMoved = true;

        // Opening and immediately releasing must not overwrite an existing mode.
        if (!cursorMoved) {
            onClose();
            return;
        }
        super.saveAndClose(mouseX, mouseY);
    }
}
