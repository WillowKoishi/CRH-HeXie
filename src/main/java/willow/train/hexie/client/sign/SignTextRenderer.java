package willow.train.hexie.client.sign;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;

/** Shared world-space sign lettering. Caller supplies a pose with +Y up, face toward +Z. */
public final class SignTextRenderer {
    /** A persistent mesh buffer must keep its text textures until the mesh is closed. */
    public interface TextureOwner extends MultiBufferSource {
        void retainSignTexture(ResourceLocation texture);
    }
    private static final java.util.Map<ResourceLocation, Integer> RETAINED = new java.util.HashMap<>();
    public static void retain(ResourceLocation texture) { RETAINED.merge(texture, 1, Integer::sum); }
    public static void release(ResourceLocation texture) {
        RETAINED.computeIfPresent(texture, (key, count) -> count > 1 ? count - 1 : null);
    }
    private static String normalize(String text) {
        return text == null ? "" : text.codePoints()
                .filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT && c != 0xA7)
                .limit(128).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
    public enum Alignment { LEFT, CENTER, RIGHT }
    private record Key(ResourceLocation profile, String text) {}
    private static final class Entry {
        final ResourceLocation texture;
        final int width, height, inkWidth, inkHeight;
        private final DynamicTexture dynamic;
        private RenderType smoothType;
        private long mipBytes;

        Entry(ResourceLocation texture, DynamicTexture dynamic, int width, int height, int inkWidth, int inkHeight) {
            this.texture = texture;
            this.dynamic = dynamic;
            this.width = width;
            this.height = height;
            this.inkWidth = inkWidth;
            this.inkHeight = inkHeight;
        }

        long bytes() { return (long) width * height * 4 + mipBytes; }

        RenderType renderType(boolean smooth) {
            if (!smooth) return RenderType.text(texture);
            if (smoothType == null) {
                prepareMipmaps();
                smoothType = SmoothTextType.createFor(texture);
            }
            return smoothType;
        }

        private void prepareMipmaps() {
            int levels = Math.min(4, 31 - Integer.numberOfLeadingZeros(Math.min(width, height)));
            NativeImage[] images = new NativeImage[levels + 1];
            images[0] = dynamic.getPixels();
            try {
                for (int level = 1; level <= levels; level++) {
                    images[level] = new NativeImage(width >> level, height >> level, false);
                    writeCoverageMip(images[level - 1], images[level]);
                }
                // Reallocate this same texture with mip storage, then restore its unchanged base image.
                TextureUtil.prepareImage(dynamic.getId(), levels, width, height);
                for (int level = 0; level <= levels; level++) {
                    NativeImage image = images[level];
                    // Apply once after the linear mip chain is complete; never feed adjusted coverage
                    // into the next level. Small display numerals otherwise lose their visible weight.
                    if (level > 0) strengthenCoverage(image);
                    image.upload(level, 0, 0, 0, 0, image.getWidth(), image.getHeight(),
                            true, false, levels > 0, false);
                }
                for (int level = 1; level <= levels; level++)
                    mipBytes += (long) images[level].getWidth() * images[level].getHeight() * 4;
                SignTextRenderer.bytes += mipBytes;
            } finally {
                // Level zero remains owned by DynamicTexture; temporary CPU mips are no longer needed.
                for (int level = 1; level <= levels; level++)
                    if (images[level] != null) images[level].close();
            }
        }
    }

    private static void strengthenCoverage(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int alpha = image.getPixelRGBA(x, y) >>> 24;
            int adjusted = (int) Math.round(255 * Math.pow(alpha / 255.0, .8));
            image.setPixelRGBA(x, y, (adjusted << 24) | 0x00ffffff);
        }
    }

    /** Area-average coverage without the block-texture mip generator's alpha-cutout threshold. */
    private static void writeCoverageMip(NativeImage source, NativeImage target) {
        double scaleX = (double) source.getWidth() / target.getWidth();
        double scaleY = (double) source.getHeight() / target.getHeight();
        for (int y = 0; y < target.getHeight(); y++) {
            double top = y * scaleY, bottom = (y + 1) * scaleY;
            for (int x = 0; x < target.getWidth(); x++) {
                double left = x * scaleX, right = (x + 1) * scaleX, coverage = 0;
                for (int sy = (int) top; sy < Math.min(source.getHeight(), (int) Math.ceil(bottom)); sy++) {
                    double weightY = Math.min(bottom, sy + 1) - Math.max(top, sy);
                    for (int sx = (int) left; sx < Math.min(source.getWidth(), (int) Math.ceil(right)); sx++) {
                        double weightX = Math.min(right, sx + 1) - Math.max(left, sx);
                        coverage += (source.getPixelRGBA(sx, sy) >>> 24) * weightX * weightY;
                    }
                }
                int alpha = (int) Math.round(coverage / (scaleX * scaleY));
                target.setPixelRGBA(x, y, (alpha << 24) | 0x00ffffff);
            }
        }
    }
    private static final LinkedHashMap<Key, Entry> CACHE = new LinkedHashMap<>(32, .75f, true);
    private static long serial, bytes;
    private static final long BUDGET = 32L * 1024 * 1024;

    /** Local units may be blocks or model pixels. Height means visible ink height, not font line height. */
    public static void drawLine(ResourceLocation profile, String text, PoseStack pose, MultiBufferSource buffers,
                                float x, float y, float height, float maxWidth, int color, int light, Alignment alignment) {
        drawLine(profile, text, pose, buffers, x, y, height, maxWidth, color, light, alignment, false);
    }

    /** Enables filtered mip sampling for this rasterized line without changing global font settings. */
    public static void drawLine(ResourceLocation profile, String text, PoseStack pose, MultiBufferSource buffers,
                                float x, float y, float height, float maxWidth, int color, int light,
                                Alignment alignment, boolean smooth) {
        if (text == null || text.isBlank() || !Float.isFinite(height) || !Float.isFinite(maxWidth) || height <= 0 || maxWidth <= 0) return;
        text = normalize(text);
        if (text.isBlank()) return;
        var loaded = SignFontProfiles.get(profile);
        // A missing profile must not make a sign unreadable after a resource-pack error.
        if (loaded == null || loaded.font().canDisplayUpTo(text) != -1) {
            fallback(text, pose, buffers, x, y, height, maxWidth, color, light, alignment); return;
        }
        Entry entry = entry(profile, text, loaded);
        if (entry == null) return;
        if (buffers instanceof TextureOwner owner) owner.retainSignTexture(entry.texture);
        float scale = Math.min(height / entry.inkHeight, maxWidth / entry.inkWidth);
        float width = entry.inkWidth * scale;
        float left = x - (alignment == Alignment.CENTER ? width / 2 : alignment == Alignment.RIGHT ? width : 0);
        float x0 = left - 2 * scale, x1 = x0 + entry.width * scale, y0 = y + 2 * scale, y1 = y0 - entry.height * scale;
        var vertex = buffers.getBuffer(entry.renderType(smooth)); var matrix = pose.last().pose();
        vertex.addVertex(matrix, x0, y1, 0).setColor(color).setUv(0, 1).setLight(light);
        vertex.addVertex(matrix, x1, y1, 0).setColor(color).setUv(1, 1).setLight(light);
        vertex.addVertex(matrix, x1, y0, 0).setColor(color).setUv(1, 0).setLight(light);
        vertex.addVertex(matrix, x0, y0, 0).setColor(color).setUv(0, 0).setLight(light);
    }
    public static float width(ResourceLocation profile, String text, float height, float maxWidth) {
        text = normalize(text);
        if (text.isBlank() || !Float.isFinite(height) || !Float.isFinite(maxWidth) || height <= 0 || maxWidth <= 0) return 0;
        var loaded = SignFontProfiles.get(profile);
        if (loaded == null || loaded.font().canDisplayUpTo(text) != -1)
            return Math.min(maxWidth, Minecraft.getInstance().font.width(text) * height / Minecraft.getInstance().font.lineHeight);
        Entry entry = entry(profile, text, loaded);
        return entry == null ? 0 : Math.min(maxWidth, height * entry.inkWidth / entry.inkHeight);
    }
    private static Entry entry(ResourceLocation profile, String text, SignFontProfiles.Loaded loaded) {
        Key key = new Key(profile, text);
        Entry entry = CACHE.get(key);
        if (entry == null) {
            var raster = SignTextRasterizer.rasterize(loaded.font(), text, loaded.profile().trackingEm(), loaded.profile().widthScale());
            if (raster == null) return null;
            var image = raster.image(); NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
            for (int py = 0; py < image.getHeight(); py++) for (int px = 0; px < image.getWidth(); px++) {
                int argb = image.getRGB(px, py);
                nativeImage.setPixelRGBA(px, py, (argb & 0xFF00FF00) | ((argb >>> 16) & 255) | ((argb & 255) << 16));
            }
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("hexie", "dynamic/sign_text/" + serial++);
            DynamicTexture dynamic = new DynamicTexture(nativeImage); dynamic.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(texture, dynamic);
            entry = new Entry(texture, dynamic, image.getWidth(), image.getHeight(), raster.inkWidth(), raster.inkHeight());
            CACHE.put(key, entry); bytes += entry.bytes();
        }
        return entry;
    }
    private static void fallback(String text, PoseStack pose, MultiBufferSource buffers, float x, float y,
                                 float height, float maxWidth, int color, int light, Alignment alignment) {
        Font font = Minecraft.getInstance().font;
        float width = font.width(text), scale = Math.min(height / font.lineHeight, maxWidth / Math.max(1, width));
        pose.pushPose(); pose.translate(x, y, 0); pose.scale(scale, -scale, scale);
        font.drawInBatch(text, alignment == Alignment.CENTER ? -width / 2 : alignment == Alignment.RIGHT ? -width : 0,
                0, color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light); pose.popPose();
    }
    /** Called between frames, never while quads referencing these textures are pending in buffers. */
    public static void trim() {
        var iterator = CACHE.entrySet().iterator();
        while ((bytes > BUDGET || CACHE.size() > 256) && iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (RETAINED.containsKey(entry.texture)) continue;
            Minecraft.getInstance().getTextureManager().release(entry.texture);
            bytes -= entry.bytes(); iterator.remove();
        }
    }
    public static void clear() {
        for (Entry entry : CACHE.values()) Minecraft.getInstance().getTextureManager().release(entry.texture);
        CACHE.clear(); RETAINED.clear(); bytes = 0;
    }

    /** The normal text render state, with trilinear filtering fixed for this texture only. */
    private static final class SmoothTextType extends RenderType {
        private SmoothTextType() {
            super("hexie_smooth_sign_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                    VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {});
        }

        static RenderType createFor(ResourceLocation texture) {
            return create("hexie_smooth_sign_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                    VertexFormat.Mode.QUADS, 256, false, true,
                    CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SHADER)
                            .setTextureState(new TextureStateShard(texture, true, true))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setLightmapState(LIGHTMAP).createCompositeState(false));
        }
    }
    private SignTextRenderer() {}
}
