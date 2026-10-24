package willow.train.hexie.client.overlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

public interface HexieOverlay {
    void render(Minecraft mc, GuiGraphics pose, float partialTicks);

    String getDescriptionName();

    boolean canSurvive();
}
