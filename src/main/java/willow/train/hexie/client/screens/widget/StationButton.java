package willow.train.hexie.client.screens.widget;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import willow.train.hexie.client.util.gui.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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

    @Override
    public void renderWidget(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        this.width = (Math.round(40 * scale));
        this.height = (Math.round(40 * scale));
        float itemScale = cache.interchange ? 0.25f : 0.3f;
        float itemOffset = (1 - itemScale) / 2;
        ResourceLocation background = cache.interchange ? interchangeStationButton : stationButton;
        int markerSize = this.width / 2;
        pGuiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        pGuiGraphics.blit(background, getX() + this.width / 4, getY() + this.height / 4,
                0.0f, 0.0f, markerSize, markerSize, markerSize, markerSize);
        this.renderItem(
                pGuiGraphics,
                cache.station().logo(),
                getX() + Math.round(itemOffset * this.width),
                getY() + Math.round(itemOffset * this.height),
                Math.round(itemScale * this.width),
                Math.round(itemScale * this.height));
        this.drawTextLazy(
                pGuiGraphics,
                font,
                stationName,
                getX(),
                getY() + (int) Math.round(0.85 * this.height),
                0.8f * scale,
                themeColor);
    }

    private void renderItem(GuiGraphics graphics, ItemStack stack, int x, int y, int width, int height) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(width / 16.0F, height / 16.0F, 1.0F);
        graphics.renderItem(stack, 0, 0);
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
