package willow.train.hexie.client.overlay;

import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.awt.*;
import java.util.List;
import java.util.function.Function;

public abstract class BaseOverlay extends Overlay implements HexieOverlay {

    /**
     * 这是 gui 里渲染图片的懒人方法
     * @param textureLocation  图片的 ResourceLocation
     * @param pose 栈
     * @param x 图片在屏幕上的 x 轴位置，窗口最左侧为 0，单位为像素 (px)
     * @param y 图片在屏幕上的 y 轴位置，窗口顶部为 0，单位为像素 (px)
     * @param width 图片的宽度，单位为像素 (px)
     * @param height 图片的高度，单位为像素 (px)
     */

    void renderTextureLazy(ResourceLocation textureLocation, GuiGraphics pose, int x, int y, int width, int height){
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, textureLocation);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(x, y, 0.0F).setUv(0, 0);
        buffer.addVertex(x, y + height, 0.0F).setUv(0, 1);
        buffer.addVertex(x + width, y + height, 0.0F).setUv(1, 1);
        buffer.addVertex(x + width, y, 0.0F).setUv(1, 0);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /**
     * 这是 gui 里渲染文字的方法
     * @param pose 栈
     * @param font 字体
     * @param component 需要显示出来的 Component
     * @param x 字的 x 轴位置
     * @param y 字的 y 轴位置
     * @param hexColor hex 格式的颜色
     */
    void renderComponent(GuiGraphics pose, Font font, Component component, int x, int y, int hexColor) {
        willow.train.hexie.client.util.gui.GuiText.draw(pose, font, getRenderMessages(component,
                (p_173653_) -> {
                    List<FormattedCharSequence> list = font.split(p_173653_, 512);
                    return list.isEmpty() ? FormattedCharSequence.EMPTY : list.get(0);
                }), x, y, hexColor);
    }

    FormattedCharSequence getRenderMessages(Component component,
                                                    Function<Component, FormattedCharSequence> pMessageTransformer) {
        return pMessageTransformer.apply(component);
    }
}
