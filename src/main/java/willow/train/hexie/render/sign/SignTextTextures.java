package willow.train.hexie.render.sign;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import static willow.train.hexie.render.sign.SignTextMetrics.height;
import static willow.train.hexie.render.sign.SignTextMetrics.width;

// split from willow/train/hexie/client/sign/SignTextRenderer.java, lines 32-36, 43-141, 263-284, 304-339
public final class SignTextTextures {
    private static final java.util.Map<ResourceLocation, Integer> RETAINED = new java.util.HashMap<>();
    public static void retain(ResourceLocation texture) { RETAINED.merge(texture, 1, Integer::sum); }
    public static void release(ResourceLocation texture) {
        RETAINED.computeIfPresent(texture, (key, count) -> count > 1 ? count - 1 : null);
    }

    private record Key(ResourceLocation profile, String text, float tracking) {}
    static final class Entry {
        final ResourceLocation texture;
        final int width, height, inkWidth, inkHeight;
        final float firstDigitCenter, lastDigitCenter;
        private final DynamicTexture dynamic;
        private RenderType smoothType;
        private RenderType surfaceType;
        private long mipBytes;

        Entry(ResourceLocation texture, DynamicTexture dynamic, int width, int height, int inkWidth, int inkHeight, float firstDigitCenter, float lastDigitCenter) {
            this.texture = texture;
            this.dynamic = dynamic;
            this.width = width;
            this.height = height;
            this.inkWidth = inkWidth;
            this.inkHeight = inkHeight;
            this.firstDigitCenter=firstDigitCenter;this.lastDigitCenter=lastDigitCenter;
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

        RenderType surfaceType() {
            // Ensure mip storage exists once, shared with the ordinary smooth lettering type.
            renderType(true);
            if (surfaceType == null) surfaceType = SmoothTextType.createFor(texture, true);
            return surfaceType;
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
                SignTextTextures.bytes += mipBytes;
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
    static final LinkedHashMap<Key, Entry> CACHE = new LinkedHashMap<>(32, .75f, true);
    static long serial, bytes;
    private static final long BUDGET = 32L * 1024 * 1024;

    static Entry entry(ResourceLocation profile, String text, SignFontProfiles.Loaded loaded) {
        return entry(profile, text, loaded, 0);
    }
    static Entry entry(ResourceLocation profile, String text, SignFontProfiles.Loaded loaded, float extraTracking) {
        Key key = new Key(profile, text, extraTracking);
        Entry entry = CACHE.get(key);
        if (entry == null) {
            var raster = SignTextRasterizer.rasterize(loaded.font(), text, loaded.profile().trackingEm() + extraTracking, loaded.profile().widthScale());
            if (raster == null) return null;
            var image = raster.image(); NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
            for (int py = 0; py < image.getHeight(); py++) for (int px = 0; px < image.getWidth(); px++) {
                int argb = image.getRGB(px, py);
                nativeImage.setPixelRGBA(px, py, (argb & 0xFF00FF00) | ((argb >>> 16) & 255) | ((argb & 255) << 16));
            }
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("hexie", "dynamic/sign_text/" + serial++);
            DynamicTexture dynamic = new DynamicTexture(nativeImage); dynamic.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(texture, dynamic);
            entry = new Entry(texture, dynamic, image.getWidth(), image.getHeight(), raster.inkWidth(), raster.inkHeight(), raster.firstDigitCenter(), raster.lastDigitCenter());
            CACHE.put(key, entry); bytes += entry.bytes();
        }
        return entry;
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

        static RenderType createFor(ResourceLocation texture) { return createFor(texture, false); }

        static RenderType createFor(ResourceLocation texture, boolean surface) {
            return create(surface ? "hexie_surface_sign_text" : "hexie_smooth_sign_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                    VertexFormat.Mode.QUADS, 256, false, true,
                    CompositeState.builder().setShaderState(RENDERTYPE_TEXT_SHADER)
                            .setTextureState(new TextureStateShard(texture, true, true))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            // Decal coverage must not become an opaque depth occluder at soft glyph edges.
                            // Keep LEQUAL depth testing, so the lettering remains hidden behind other geometry.
                            .setWriteMaskState(surface ? COLOR_WRITE : COLOR_DEPTH_WRITE)
                            .setLightmapState(LIGHTMAP).createCompositeState(false));
        }
    }
}
