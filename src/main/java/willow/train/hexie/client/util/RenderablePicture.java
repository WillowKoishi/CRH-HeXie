package willow.train.hexie.client.util;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import javax.annotation.Nonnull;

public class RenderablePicture {

    private final int uOffset, vOffset, uWidth, vHeight;
    private int color;
    private float fuOffset, fvOffset, fuWidth, fvHeight;
    private int cachedImageWidth = -1, cachedImageHeight = -1;
    private float zoom = 1.0f, alpha;
    public static final int COLOR_DEFAULT = 0xffffff;
    private final GuiImageCache.ImageData data;

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, int uOffset, int vOffset, int uWidth, int vHeight, int color, float alpha) {
        this.data = imageData;
        this.uOffset = uOffset;
        this.vOffset = vOffset;
        this.uWidth = uWidth;
        this.vHeight = vHeight;
        this.color = color;
        this.alpha = alpha;
        refreshImage();
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, int uOffset, int vOffset, int uWidth, int vHeight) {
        this(imageData, uOffset, vOffset, uWidth, vHeight, COLOR_DEFAULT, 1.0f);
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, int uWidth, int vHeight) {
        this(imageData, 0, 0, uWidth, vHeight, COLOR_DEFAULT, 1.0f);
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData) {
        this(imageData, 0, 0, imageData.width, imageData.height, COLOR_DEFAULT, 1.0f);
    }

    public double widthHeightRatio() {
        return data.aspectRatio();
    }

    public int width() {
        return (int) (zoom * uWidth);
    }

    public int height() {
        return (int) (zoom * vHeight);
    }

    public void zoom(float zoom) {
        this.zoom = Math.min(Math.max(zoom, 0), 5);
    }

    public void setColor(int r, int g, int b, float a) {
        this.color = packRgb(r, g, b);
        this.alpha = a;
    }

    public void refreshImage() {
        if (data.width <= 0 || data.height <= 0)
            return;
        fuOffset = ((float) uOffset)/((float) data.width);
        fvOffset = ((float) vOffset)/((float) data.height);
        fuWidth = ((float) uWidth)/((float) data.width);
        fvHeight = ((float) vHeight)/((float) data.height);
        cachedImageWidth = data.width;
        cachedImageHeight = data.height;
    }

    public void render(GuiGraphics graphics, int x, int y, int width, int height) {
        if (data.width <= 0 || data.height <= 0)
            return;
        if (cachedImageWidth != data.width || cachedImageHeight != data.height)
            refreshImage();

        graphics.setColor(
                ((color >> 16) & 0xff) / 255.0f,
                ((color >> 8) & 0xff) / 255.0f,
                (color & 0xff) / 255.0f,
                1.0f);
        try {
            graphics.blit(data.location, x, y, width, height,
                    (float) uOffset, (float) vOffset,
                    uWidth, vHeight, data.width, data.height);
        } finally {
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    public void renderLazy(GuiGraphics graphics, int x, int y) {
        render(graphics, x, y, (int) (uWidth * zoom), (int) (vHeight * zoom));
    }

    private int packRgb(int r, int g, int b) {
        return 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
    }
}
