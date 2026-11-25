package willow.train.hexie.render.sign;

// split from willow/train/hexie/client/sign/SignTextRenderer.java

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.bridge.render.DisplayRenderBridge.SignTextRendererTextureOwnerView;

import static willow.train.hexie.render.sign.SignTextMetrics.height;
import static willow.train.hexie.render.sign.SignTextMetrics.width;
import static willow.train.hexie.render.sign.SignTextTextures.Entry;
import static willow.train.hexie.render.sign.SignTextTextures.entry;

/** Shared world-space sign lettering. Caller supplies a pose with +Y up, face toward +Z. */
public final class SignTextRenderer {
    /** Existing surface lettering pass, also accepting an authored RGBA decal texture. */
    public static void drawDecal(ResourceLocation texture,PoseStack pose,MultiBufferSource buffers,
            double left,double top,double width,double height,int light,Surface surface) {
        var vertex=buffers.getBuffer(RenderType.text(texture));
        for(float[] uv:new float[][]{{0,1},{1,1},{1,0},{0,0}}) {
            var point=surface.point((float)(left+width*uv[0]),(float)(top-height*uv[1]));
            vertex.addVertex(pose.last().pose(),(float)point.x,(float)point.y,(float)point.z)
                    .setColor(0xFFFFFFFF).setUv(uv[0],uv[1]).setLight(light);
        }
    }
    /** A persistent mesh buffer must keep its text textures until the mesh is closed. */
    public interface TextureOwner extends SignTextRendererTextureOwnerView {
        void retainSignTexture(ResourceLocation texture);
    }
    public static String normalize(String text) {
        return text == null ? "" : text.codePoints()
                .filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT && c != 0xA7)
                .limit(128).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
    public enum Alignment { LEFT, CENTER, RIGHT }

    /** Local units may be blocks or model pixels. Height means visible ink height, not font line height. */
    public static void drawLine(ResourceLocation profile, String text, PoseStack pose, MultiBufferSource buffers,
                                float x, float y, float height, float maxWidth, int color, int light, Alignment alignment) {
        drawLine(profile, text, pose, buffers, x, y, height, maxWidth, color, light, alignment, false);
    }

    /** Enables filtered mip sampling for this rasterized line without changing global font settings. */
    public static void drawLine(ResourceLocation profile, String text, PoseStack pose, MultiBufferSource buffers,
                                float x, float y, float height, float maxWidth, int color, int light,
                                Alignment alignment, boolean smooth) {
        drawLine(profile, text, pose, buffers, x, y, height, maxWidth, color, light, alignment, smooth, null);
    }

    /** Maps lettering coordinates onto a model surface, before the caller's pose transform. */
    public interface Surface { net.minecraft.world.phys.Vec3 point(float x, float y); }
    public static final Surface FLAT_SURFACE = (x,y) -> new net.minecraft.world.phys.Vec3(x,y,0);

    public static void drawLine(ResourceLocation profile, String text, PoseStack pose, MultiBufferSource buffers,
                                float x, float y, float height, float maxWidth, int color, int light,
                                Alignment alignment, boolean smooth, Surface surface) {
        drawLine(profile, text, pose, buffers, x, y, height, maxWidth, color, light, alignment, smooth, surface, 0);
    }

    public static void drawLine(ResourceLocation profile, String text, PoseStack pose, MultiBufferSource buffers,
                                float x, float y, float height, float maxWidth, int color, int light,
                                Alignment alignment, boolean smooth, Surface surface, float extraTracking) {
        if (text == null || text.isBlank() || !Float.isFinite(height) || !Float.isFinite(maxWidth) || height <= 0 || maxWidth <= 0) return;
        text = normalize(text);
        if (text.isBlank()) return;
        var loaded = SignFontProfiles.get(profile);
        // A missing profile must not make a sign unreadable after a resource-pack error.
        if (loaded == null || loaded.font().canDisplayUpTo(text) != -1) {
            if (surface == null) fallback(text, pose, buffers, x, y, height, maxWidth, color, light, alignment, extraTracking);
            else fallback(text, new PoseStack(), type -> surfaceConsumer(buffers.getBuffer(type), pose, surface),
                    x, y, height, maxWidth, color, light, alignment, extraTracking);
            return;
        }
        Entry entry = entry(profile, text, loaded, extraTracking);
        if (entry == null) return;
        if (buffers instanceof SignTextRendererTextureOwnerView owner) owner.retainSignTexture(entry.texture);
        float scale = Math.min(height / entry.inkHeight, maxWidth / entry.inkWidth);
        float width = entry.inkWidth * scale;
        float left = x - (alignment == Alignment.CENTER ? width / 2 : alignment == Alignment.RIGHT ? width : 0);
        float x0 = left - 2 * scale, x1 = x0 + entry.width * scale, y0 = y + 2 * scale, y1 = y0 - entry.height * scale;
        var vertex = buffers.getBuffer(surface == null ? entry.renderType(smooth) : entry.surfaceType()); var matrix = pose.last().pose();
        if (surface == null) {
            vertex.addVertex(matrix, x0, y1, 0).setColor(color).setUv(0, 1).setLight(light);
            vertex.addVertex(matrix, x1, y1, 0).setColor(color).setUv(1, 1).setLight(light);
            vertex.addVertex(matrix, x1, y0, 0).setColor(color).setUv(1, 0).setLight(light);
            vertex.addVertex(matrix, x0, y0, 0).setColor(color).setUv(0, 0).setLight(light);
            return;
        }
        int columns = surface == FLAT_SURFACE ? 1 : 32;
        int rows = surface == FLAT_SURFACE ? 1 : 4;
        for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++) {
            float u0 = (float) col / columns, u1 = (float) (col + 1) / columns;
            float v0 = (float) row / rows, v1 = (float) (row + 1) / rows;
            for (float[] uv : new float[][]{{u0,v1},{u1,v1},{u1,v0},{u0,v0}}) {
                float px = x0 + (x1 - x0) * uv[0], py = y0 + (y1 - y0) * uv[1];
                var point = surface == null ? new net.minecraft.world.phys.Vec3(px, py, 0) : surface.point(px, py);
                vertex.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                        .setColor(color).setUv(uv[0], uv[1]).setLight(light);
            }
        }
    }
    private static com.mojang.blaze3d.vertex.VertexConsumer surfaceConsumer(
            com.mojang.blaze3d.vertex.VertexConsumer delegate, PoseStack pose, Surface surface) {
        return new com.mojang.blaze3d.vertex.VertexConsumer() {
            public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
                var p = surface.point(x, y);
                delegate.addVertex(pose.last().pose(), (float)p.x, (float)p.y, (float)p.z); return this;
            }
            public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r,int g,int b,int a) { delegate.setColor(r,g,b,a); return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u,float v) { delegate.setUv(u,v); return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u,int v) { delegate.setUv1(u,v); return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u,int v) { delegate.setUv2(u,v); return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x,float y,float z) { delegate.setNormal(x,y,z); return this; }
        };
    }

    private static void fallback(String text, PoseStack pose, MultiBufferSource buffers, float x, float y,
                                 float height, float maxWidth, int color, int light, Alignment alignment, float tracking) {
        Font font = Minecraft.getInstance().font;
        float gap = tracking * font.lineHeight;
        float width = font.width(text) + gap * (text.codePointCount(0, text.length()) - 1), scale = Math.min(height / font.lineHeight, maxWidth / Math.max(1, width));
        pose.pushPose(); pose.translate(x, y, 0); pose.scale(scale, -scale, scale);
        float cursor = alignment == Alignment.CENTER ? -width / 2 : alignment == Alignment.RIGHT ? -width : 0;
        if (tracking == 0) {
            font.drawInBatch(text, cursor, 0, color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        } else {
            for (int i = 0; i < text.length();) {
                String glyph = Character.toString(text.codePointAt(i));
                font.drawInBatch(glyph, cursor, 0, color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
                cursor += font.width(glyph) + gap;
                i += glyph.length();
            }
        }
        pose.popPose();
    }
    private SignTextRenderer() {}
}
