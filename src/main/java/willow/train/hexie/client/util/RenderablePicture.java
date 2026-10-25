package willow.train.hexie.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
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

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, int uOffset, int vOffset, int uWidth, int vHeight, int color) {
        this(imageData, uOffset, vOffset, uWidth, vHeight, color, 1.0f);
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, int uOffset, int vOffset, int uWidth, int vHeight) {
        this(imageData, uOffset, vOffset, uWidth, vHeight, COLOR_DEFAULT, 1.0f);
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, int uWidth, int vHeight, int color, float alpha) {
        this(imageData, 0, 0, uWidth, vHeight, color, alpha);
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, int uWidth, int vHeight, int color) {
        this(imageData, 0, 0, uWidth, vHeight, color, 1.0f);
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, int uWidth, int vHeight) {
        this(imageData, 0, 0, uWidth, vHeight, COLOR_DEFAULT, 1.0f);
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, int color, float alpha) {
        this(imageData, 0, 0, imageData.width, imageData.height, color, alpha);
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, int color) {
        this(imageData, 0, 0, imageData.width, imageData.height, color, 1.0f);
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData, float alpha) {
        this(imageData, 0, 0, imageData.width, imageData.height, COLOR_DEFAULT, alpha);
    }

    public RenderablePicture(@Nonnull GuiImageCache.ImageData imageData) {
        this(imageData, 0, 0, imageData.width, imageData.height, COLOR_DEFAULT, 1.0f);
    }

    public RenderablePicture cut(int left, int up, int right, int down) {
        return new RenderablePicture(data, uOffset + left, vOffset + up, uWidth - right - left, vHeight - down - up, color, alpha);
    }

    public GuiImageCache.ImageData getData() {
        return data;
    }

    public double widthHeightRatio() {
        return data.aspectRatio();
    }

    public double heightWidthRatio() {
        return 1 / data.aspectRatio();
    }

    public int getFixedHeight(int fromWidth) {
        return (int) ((double)fromWidth / widthHeightRatio());
    }

    public int getFixedWidth(int fromHeight) {
        return (int) ((double) fromHeight * widthHeightRatio());
    }

    public int uOffset() {
        return uOffset;
    }

    public int vOffset() {
        return vOffset;
    }

    public float fuOffset() {
        return (float) uOffset / (float) data.width;
    }

    public float fvOffset() {
        return (float) vOffset / (float) data.height;
    }

    public float fuWidth() {
        return (float) uWidth / (float) data.width;
    }

    public float fvHeight() {
        return (float) vHeight / (float) data.height;
    }

    public float fuOffsetR(){
        return fuOffset() + fuWidth();
    }

    public float fvOffsetD() {
        return fvOffset() + fvHeight();
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

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
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

    public void setColor(int r, int g, int b) {
        this.color = packRgb(r, g, b);
    }

    public void render(int x, int y, int width, int height) {
        if (data.width <= 0 || data.height <= 0)
            return;
        if (cachedImageWidth != data.width || cachedImageHeight != data.height)
            refreshImage();
        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = color & 0xff;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, data.location);
        RenderSystem.setShaderColor(
                (float) r / 255.0f, (float) g / 255.0f, (float) b / 255.0f, 1.0f);
        RenderSystem.enableBlend();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(x, y, 0.0F).setUv(fuOffset, fvOffset);
        buffer.addVertex(x, y + height, 0.0F).setUv(fuOffset, fvOffset + fvHeight);
        buffer.addVertex(x + width, y + height, 0.0F).setUv(fuOffset + fuWidth,fvOffset + fvHeight);
        buffer.addVertex(x + width, y, 0.0F).setUv(fuOffset + fuWidth, fvOffset);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
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

    public void renderScaled(int x, int y, int axis, boolean isWidth) {
        if(isWidth) render(x, y, axis, getFixedHeight(axis));
        else render(x, y, getFixedWidth(axis), axis);
    }

    public void renderLazy(int x, int y) {
        render(x, y, (int) (uWidth * zoom), (int) (vHeight * zoom));
    }

    public void renderLazy(GuiGraphics graphics, int x, int y) {
        render(graphics, x, y, (int) (uWidth * zoom), (int) (vHeight * zoom));
    }

    private int packRgb(int r, int g, int b) {
        return 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
    }
}
