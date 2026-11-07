package willow.train.hexie.blocks.signs;

import com.google.gson.JsonParser;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Headless outline measurements of packaged marking fonts; no client classes, pixels or GPU resources. */
final class PanelAttachmentTextMetrics {
    record Size(double width, double height) {}
    private record Profile(Font font, float tracking, float widthScale) {}
    private record Key(String profile, String text, float tracking) {}
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private static final Map<String, Profile> PROFILES = new HashMap<>();
    private static final Map<Key, Double> RATIOS = new LinkedHashMap<>(32, .75f, true);
    private static final Size EMPTY = new Size(0, 0);

    static synchronized Size measure(String profile, String value, double height, double maxWidth, float tracking) {
        if (value == null || value.isBlank() || height <= 0) return EMPTY;
        String text = value.codePoints().filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT && c != 0xA7)
                .limit(128).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        if (text.isBlank()) return EMPTY;
        var key = new Key(profile, text, tracking);
        double ratio = RATIOS.computeIfAbsent(key, ignored -> ratio(profile, text, tracking));
        if (RATIOS.size() > 256) RATIOS.remove(RATIOS.keySet().iterator().next());
        if (!(ratio > 0) || !Double.isFinite(ratio)) return EMPTY;
        double actualHeight = Math.min(height, maxWidth / ratio);
        if (PROFILES.get(profile) == null) actualHeight = Math.min(actualHeight, Math.min(.25, .5 / ratio));
        return new Size(actualHeight * ratio, actualHeight);
    }

    private static double ratio(String id, String text, float tracking) {
        if (!PROFILES.containsKey(id)) {
            if (PROFILES.size() >= 64) PROFILES.clear();
            PROFILES.put(id, load(id));
        }
        var profile = PROFILES.get(id);
        if (profile != null) {
            Font font = profile.font.deriveFont(72f).deriveFont(Map.of(TextAttribute.TRACKING, profile.tracking + tracking));
            var bounds = new TextLayout(text, font, CONTEXT).getBounds();
            if (bounds.isEmpty()) return 0;
            // Match the existing marking renderer's bounded outline raster dimensions.
            double scale = Math.min(1, 2044 / Math.max(1, bounds.getWidth() * profile.widthScale));
            return Math.max(1, Math.ceil(bounds.getWidth() * profile.widthScale * scale))
                    / Math.max(1, Math.ceil(bounds.getHeight() * scale));
        }
        // Unknown/native profiles retain a small logical text region instead of selecting the whole panel.
        double advance = text.codePoints().mapToDouble(c -> c == ' ' ? 4 : c < 128 ? 6 : 9).sum();
        return Math.max(1, advance + tracking * 9 * (text.codePointCount(0, text.length()) - 1)) / 9;
    }

    private static Profile load(String id) {
        var resource = ResourceLocation.tryParse(id);
        if (resource == null) return null;
        String path = "/assets/" + resource.getNamespace() + "/sign_fonts/" + resource.getPath() + ".json";
        try (var stream = resource(path)) {
            if (stream == null) return null;
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var fontId = ResourceLocation.parse(json.get("font").getAsString());
            try (var fontStream = resource("/assets/" + fontId.getNamespace() + "/" + fontId.getPath())) {
                if (fontStream == null) return null;
                var font = Font.createFont(Font.TRUETYPE_FONT, fontStream)
                        .deriveFont(json.has("bold") && json.get("bold").getAsBoolean() ? Font.BOLD : Font.PLAIN);
                return new Profile(font, json.has("tracking_em") ? json.get("tracking_em").getAsFloat() : 0,
                        json.has("width_scale") ? json.get("width_scale").getAsFloat() : 1);
            }
        } catch (Exception ignored) { return null; }
    }

    private static java.io.InputStream resource(String path) throws java.io.IOException {
        var own = PanelAttachmentTextMetrics.class.getResourceAsStream(path);
        if (own != null) return own;
        // Release fonts live in a separate resource-only mod/module. They are optional on servers.
        var mods = net.neoforged.fml.ModList.get();
        var fonts = mods == null ? null : mods.getModFileById("hexie_fonts");
        if (fonts == null) return null;
        var file = fonts.getFile().findResource(path.substring(1).split("/"));
        return java.nio.file.Files.isRegularFile(file) ? java.nio.file.Files.newInputStream(file) : null;
    }
    private PanelAttachmentTextMetrics() {}
}
