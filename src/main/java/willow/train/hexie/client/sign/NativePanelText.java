package willow.train.hexie.client.sign;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import willow.train.hexie.compat.iris.client.PassengerPanelTextLayer;

/** Native font atlas quads, uniformly scaled and clipped to a recessed display row. */
public final class NativePanelText {
    public static final int MAX_CODE_POINTS = 256;

    /** Coordinates are screen-local with +Y up. Height describes the visible glyph quad bounds. */
    public static void drawLine(String text, PoseStack pose, MultiBufferSource buffers,
                                float centerY, float width, float height, int color, double seconds) {
        draw(text, pose, buffers, centerY, width, height, color, seconds, false);
    }

    /** Short identifiers fit as a whole, without scrolling or changing the glyph aspect ratio. */
    public static void drawFitted(String text, PoseStack pose, MultiBufferSource buffers,
                                  float centerY, float width, float height, int color) {
        draw(text, pose, buffers, centerY, width, height, color, 0, true);
    }

    private static void draw(String text, PoseStack pose, MultiBufferSource buffers,
                             float centerY, float width, float height, int color, double seconds, boolean fit) {
        String plain = text == null ? "" : text.codePoints()
                .filter(cp -> !Character.isISOControl(cp) && cp != 0xA7).limit(MAX_CODE_POINTS)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().strip();
        if (plain.isEmpty() || width <= 0 || height <= 0) return;
        var capture = new Capture();
        // Minecraft owns font selection, Unicode shaping, atlas uploads and resource reloads.
        // Capture only this draw's native vertices; no font pixels or derived textures are cached.
        Minecraft.getInstance().font.drawInBatch(Component.literal(plain), 0, 0, color, false,
                new Matrix4f(), capture, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        if (capture.quads.isEmpty()) return;
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (Quad quad : capture.quads) {
            minX = Math.min(minX, quad.left); maxX = Math.max(maxX, quad.right);
            minY = Math.min(minY, quad.top); maxY = Math.max(maxY, quad.bottom);
        }
        if (maxY <= minY) return;
        float scale = height / (maxY - minY);
        if (fit && maxX > minX) scale = Math.min(scale, width / (maxX - minX));
        float textWidth = (maxX - minX) * scale;
        float left = fit || textWidth <= width ? -textWidth / 2
                : -width / 2 - scroll(textWidth - width, height, seconds);
        float top = centerY + (maxY - minY) * scale / 2;
        Matrix4f matrix = pose.last().pose();
        boolean iris = ModList.get().isLoaded("iris");
        for (Quad quad : capture.quads) {
            float x0 = left + (quad.left - minX) * scale, x1 = left + (quad.right - minX) * scale;
            float y0 = top - (quad.top - minY) * scale, y1 = top - (quad.bottom - minY) * scale;
            float clippedLeft = Math.max(x0, -width / 2), clippedRight = Math.min(x1, width / 2);
            float clippedTop = Math.min(y0, centerY + height / 2);
            float clippedBottom = Math.max(y1, centerY - height / 2);
            if (clippedLeft >= clippedRight || clippedBottom >= clippedTop || x1 <= x0 || y0 <= y1) continue;
            float u0 = lerp(quad.u0, quad.u1, (clippedLeft - x0) / (x1 - x0));
            float u1 = lerp(quad.u0, quad.u1, (clippedRight - x0) / (x1 - x0));
            float v0 = lerp(quad.v0, quad.v1, (y0 - clippedTop) / (y0 - y1));
            float v1 = lerp(quad.v0, quad.v1, (y0 - clippedBottom) / (y0 - y1));
            VertexConsumer out = buffers.getBuffer(iris ? PassengerPanelTextLayer.of(quad.type) : quad.type);
            // Flipping the native font's downward Y also gives the panel its outward winding.
            vertex(out, matrix, clippedLeft, clippedTop, u0, v0, color);
            vertex(out, matrix, clippedLeft, clippedBottom, u0, v1, color);
            vertex(out, matrix, clippedRight, clippedBottom, u1, v1, color);
            vertex(out, matrix, clippedRight, clippedTop, u1, v0, color);
        }
    }

    private static float scroll(float overflow, float rowHeight, double seconds) {
        double travel = overflow / (rowHeight * .9), pause = 1.25;
        double period = 2 * (travel + pause);
        double time = Double.isFinite(seconds) ? ((seconds % period) + period) % period : 0;
        if (time < pause) return 0;
        if (time < pause + travel) return (float) ((time - pause) / travel * overflow);
        if (time < 2 * pause + travel) return overflow;
        return (float) ((period - time) / travel * overflow);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float u, float v, int color) {
        out.addVertex(pose, x, y, 0).setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, 255)
                .setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }

    private record Quad(RenderType type, float left, float top, float right, float bottom,
                        float u0, float v0, float u1, float v1) {}

    private static final class Capture implements MultiBufferSource {
        final List<Quad> quads = new ArrayList<>();
        final Map<RenderType, VertexConsumer> consumers = new HashMap<>();
        @Override public VertexConsumer getBuffer(RenderType type) {
            return consumers.computeIfAbsent(type, key -> new VertexConsumer() {
                final float[] xs = new float[4], ys = new float[4], us = new float[4], vs = new float[4];
                int index;
                @Override public VertexConsumer addVertex(float x, float y, float z) {
                    xs[index] = x; ys[index] = y; return this;
                }
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
                @Override public VertexConsumer setUv(float u, float v) { us[index] = u; vs[index] = v; return this; }
                @Override public VertexConsumer setUv1(int u, int v) { return this; }
                @Override public VertexConsumer setUv2(int u, int v) {
                    // BakedGlyph finishes each native vertex with its light coordinates.
                    if (++index == 4) {
                        quads.add(new Quad(key, xs[0], ys[0], xs[2], ys[2], us[0], vs[0], us[2], vs[2]));
                        index = 0;
                    }
                    return this;
                }
                @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
            });
        }
    }

    private NativePanelText() {}
}
