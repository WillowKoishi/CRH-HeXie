package willow.train.hexie.client.sign;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
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
    private record Entry(ResourceLocation texture, int width, int height, int inkWidth, int inkHeight) {
        long bytes() { return (long) width * height * 4; }
    }
    private static final LinkedHashMap<Key, Entry> CACHE = new LinkedHashMap<>(32, .75f, true);
    private static long serial, bytes;
    private static final long BUDGET = 32L * 1024 * 1024;

    /** Local units may be blocks or model pixels. Height means visible ink height, not font line height. */
    public static void drawLine(ResourceLocation profile, String text, PoseStack pose, MultiBufferSource buffers,
                                float x, float y, float height, float maxWidth, int color, int light, Alignment alignment) {
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
        var vertex = buffers.getBuffer(RenderType.text(entry.texture)); var matrix = pose.last().pose();
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
            entry = new Entry(texture, image.getWidth(), image.getHeight(), raster.inkWidth(), raster.inkHeight());
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
    private SignTextRenderer() {}
}
