package willow.train.hexie.client.screens.widget;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.*;

import willow.train.hexie.client.util.gui.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import willow.train.hexie.client.util.RenderablePicture;

@OnlyIn(Dist.CLIENT)
public class ImageButton extends Button {

    RenderablePicture picture;

    public ImageButton(RenderablePicture picture, int x, int y, int width, int height, OnPress press) {
        super(0, 0, picture.width(), picture.height(), Component.empty(), press);
        this.picture = picture;
        this.width = width;
        this.height = height;
        this.setX(x);
        this.setY(y);
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        picture.setColor(isActive() ? 255 : 127, isActive() ? 255 : 127, isActive() ? 255 : 127, isActive() ? 1 : .5f);
        picture.render(pGuiGraphics, getX(), getY(), width, height);
    }
}
