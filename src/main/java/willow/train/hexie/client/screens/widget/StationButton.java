package willow.train.hexie.client.screens.widget;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;

import willow.train.hexie.Main;
import willow.train.hexie.util.station.LineTag;
import willow.train.hexie.util.station.StationRenderCache;

import java.util.ArrayList;

public class StationButton extends Button {
    ResourceLocation stationButton, interchangeStationButton;
    String stationName;
    int themeColor;
    ArrayList<LineTag> lines = new ArrayList<>();
    float scale = 1.0f;
    int staticX, staticY;
    private StationRenderCache cache;

    public StationButton(StationRenderCache cache, OnPress pOnPress) {
        super(
                (int) cache.x() - (int) (20 * cache.scale()),
                (int) cache.y() - (int) (20 * cache.scale()),
                (int) (40 * cache.scale()),
                (int) (40 * cache.scale()),
                Component.empty(),
                pOnPress);
        this.stationName = cache.station().name();
        ArrayList<LineTag> lines = cache.station().lines();
        this.themeColor = lines.size() > 0 ? lines.get(0).themeColor : 0xffffff;
        this.lines = cache.station().lines();
        this.cache = cache;
        this.stationButton = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/station_button.png");
        this.interchangeStationButton =
                ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "textures/gui/transfer_station_button.png");
        this.scale = cache.scale();
        this.staticX = (int) cache.x();
        this.staticY = (int) cache.y();
        this.setX(staticX - Math.round(20 * scale));
        this.setY(staticY - Math.round(20 * scale));
    }

    public StationButton addLine(LineTag line) {
        this.lines.add(line);
        this.station().station().addLine(line);
        return this;
    }

    public boolean belongsTo(LineTag tag) {
        return lines.contains(tag);
    }

    public void removeLine(LineTag line) {
        if (this.lines.contains(line)) {
            this.lines.remove(line);
        }
        this.station().station().removeLine(line);
    }

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        ItemRenderer renderer = mc.getItemRenderer();
        float scale_item, scale_item_2;
        this.width = (Math.round(40 * scale));
        this.height = (Math.round(40 * scale));
        if (cache.interchange) {
            scale_item = 0.25f;
            scale_item_2 = (1 - scale_item) / 2;
            RenderSystem.setShaderTexture(0, interchangeStationButton);
            RenderSystem.enableBlend();
            drawTexture(
                    getX() + this.width / 4, getY() + this.height / 4, this.width / 2, this.height / 2, 1, 1);
            this.renderItem(
                    renderer,
                    cache.station().logo(),
                    getX() + Math.round(scale_item_2 * this.width),
                    getY() + Math.round(scale_item_2 * this.height),
                    Math.round(scale_item * this.width),
                    Math.round(scale_item * this.height));
            this.drawTextLazy(
                    pGuiGraphics,
                    font,
                    stationName,
                    getX(),
                    getY() + (int) Math.round(0.85 * this.height),
                    0.8f * scale,
                    themeColor);
        } else {
            scale_item = 0.3f;
            scale_item_2 = (1 - scale_item) / 2;
            RenderSystem.setShaderTexture(0, stationButton);
            RenderSystem.enableBlend();
            drawTexture(
                    getX() + this.width / 4, getY() + this.height / 4, this.width / 2, this.height / 2, 1, 1);
            this.renderItem(
                    renderer,
                    cache.station().logo(),
                    getX() + Math.round(scale_item_2 * this.width),
                    getY() + Math.round(scale_item_2 * this.height),
                    Math.round(scale_item * this.width),
                    Math.round(scale_item * this.height));
            this.drawTextLazy(
                    pGuiGraphics,
                    font,
                    stationName,
                    getX(),
                    getY() + (int) Math.round(0.85 * this.height),
                    0.8f * scale,
                    themeColor);
        }
    }

    private void drawTexture(int x, int y, int width, int height, float uWidth, float vHeight) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(x, y, 0.0F).setUv(0, 0);
        buffer.addVertex(x, y + height, 0.0F).setUv(0, vHeight);
        buffer.addVertex(x + width, y + height, 0.0F).setUv(uWidth, vHeight);
        buffer.addVertex(x + width, y, 0.0F).setUv(uWidth, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private void renderItem(
            ItemRenderer renderer, ItemStack item, int x, int y, int width, int height) {
        BakedModel model = renderer.getModel(item, (Level) null, (LivingEntity) null, 0);
        this.renderGuiItem(renderer, item, x, y, width, height, model);
    }

    protected void renderGuiItem(ItemRenderer renderer, ItemStack stack, int x, int y, int width, int height, BakedModel model) {
        GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), Minecraft.getInstance().renderBuffers().bufferSource());
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(width / 16.0F, height / 16.0F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.flush();
        graphics.pose().popPose();
    }

    private void drawTextLazy(
            GuiGraphics poseStack, Font font, String pText, int x, int y, float scale, int color) {
        int original_width = font.width(pText);
        int original_height = font.lineHeight;
        poseStack.pose().pushPose();
        poseStack.pose().translate(
                x + ((float) this.width - ((float) original_width) * scale) / 2,
                y - ((float) original_height) * scale / 2,
                0);
        poseStack.pose().scale(scale, scale, 1.0f);
        willow.train.hexie.client.util.gui.GuiText.draw(poseStack, font, pText, 0, 0, color);
        poseStack.pose().popPose();
    }

    public Vec2 center() {
        return new Vec2(this.getX() + this.width / 2, this.getY() + this.height / 2);
    }

    public int x_center() {
        return this.getX() + this.width / 2;
    }

    public int y_center() {
        return this.getY() + this.height / 2;
    }

    public StationRenderCache station() {
        return cache;
    }

    public void setPos(int x, int y, float scale) {
        this.staticX = x;
        this.staticY = y;
        this.scale = scale;
        this.setX(staticX - Math.round(20 * scale));
        this.setY(staticY - Math.round(20 * scale));
    }
}
