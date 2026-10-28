package willow.train.hexie.client.sign;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Public extension point. All IDs and font paths respect the contributor's namespace. */
public final class SignFontProfiles extends SimplePreparableReloadListener<Map<ResourceLocation, SignFontProfiles.Loaded>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SignFontProfiles.class);
    public static final SignFontProfiles INSTANCE = new SignFontProfiles();
    private static final Map<ResourceLocation, Profile> DEFAULTS = new ConcurrentHashMap<>();
    private static volatile Map<ResourceLocation, Loaded> loaded = Map.of();
    public record Profile(ResourceLocation font, float trackingEm, float widthScale, boolean bold) {
        public Profile(ResourceLocation font, float trackingEm, float widthScale) { this(font, trackingEm, widthScale, false); }
        public Profile {
            if (font == null || !Float.isFinite(trackingEm) || trackingEm < -.1f || trackingEm > 2
                    || !Float.isFinite(widthScale) || widthScale < .25f || widthScale > 2)
                throw new IllegalArgumentException("Invalid sign font profile");
        }
    }
    public record Loaded(Font font, Profile profile) {}
    /** Register during client setup, before resource loading. Resource-pack JSON takes precedence. */
    public static void registerDefault(ResourceLocation id, Profile profile) { DEFAULTS.put(id, profile); }
    public static Loaded get(ResourceLocation id) { return loaded.get(id); }
    @Override protected Map<ResourceLocation, Loaded> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Profile> profiles = new HashMap<>(DEFAULTS);
        manager.listResources("sign_fonts", id -> id.getPath().endsWith(".json")).forEach((id, resource) -> {
            try (var reader = resource.openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                String path = id.getPath();
                ResourceLocation key = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path.substring(11, path.length() - 5));
                profiles.put(key, new Profile(ResourceLocation.parse(json.get("font").getAsString()),
                        json.has("tracking_em") ? json.get("tracking_em").getAsFloat() : 0,
                        json.has("width_scale") ? json.get("width_scale").getAsFloat() : 1,
                        json.has("bold") && json.get("bold").getAsBoolean()));
            } catch (Exception ex) { LOGGER.warn("Cannot load sign font profile {}", id, ex); }
        });
        Map<ResourceLocation, Font> fonts = new HashMap<>();
        Map<ResourceLocation, Loaded> result = new HashMap<>();
        profiles.forEach((id, profile) -> {
            try {
                Font font = fonts.get(profile.font());
                if (font == null) {
                    try (var stream = manager.getResourceOrThrow(profile.font()).open()) {
                        font = Font.createFont(Font.TRUETYPE_FONT, stream);
                    }
                    fonts.put(profile.font(), font);
                }
                result.put(id, new Loaded(font.deriveFont(profile.bold() ? Font.BOLD : Font.PLAIN), profile));
            } catch (Exception ex) { LOGGER.warn("Cannot load font for sign profile {}", id, ex); }
        });
        return Map.copyOf(result);
    }
    @Override protected void apply(Map<ResourceLocation, Loaded> prepared, ResourceManager manager, ProfilerFiller profiler) {
        SignTextRenderer.clear(); loaded = prepared;
    }
}
