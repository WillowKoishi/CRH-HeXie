package willow.train.hexie.client.carriage.browser;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import willow.train.hexie.Main;
import java.io.Reader;
import java.util.*;

/** Resource-pack page bindings. Registry IDs, never translated tab names, identify targets. */
public final class CarriagePages {
    public record Page(ResourceLocation tab, List<ResourceLocation> carriages, boolean preview, boolean switchable) {}
    private static int generation = -1;
    private static Map<ResourceLocation, Page> pages = Map.of();
    private CarriagePages() {}

    public static Page get(ResourceManager resources, ResourceLocation tab) {
        if (generation != CarriageBrowserClient.resourceGeneration) {
            Map<ResourceLocation, Page> loaded = new LinkedHashMap<>();
            var files = resources.listResources("carriage_ui/pages", id -> id.getPath().endsWith(".json"));
            files.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString))).forEach(entry -> {
                try (Reader reader = entry.getValue().openAsReader()) {
                    Page page = parse(JsonParser.parseReader(reader).getAsJsonObject());
                    if (loaded.putIfAbsent(page.tab(), page) != null)
                        throw new IllegalArgumentException("Duplicate carriage UI target tab " + page.tab());
                } catch (Exception e) { Main.LOGGER.warn("Invalid carriage UI page {}", entry.getKey(), e); }
            });
            pages = Map.copyOf(loaded);
            generation = CarriageBrowserClient.resourceGeneration;
        }
        return pages.get(tab);
    }

    public static Page parse(JsonObject json) {
        ResourceLocation tab = ResourceLocation.parse(json.get("tab").getAsString());
        LinkedHashSet<ResourceLocation> models = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray("carriages")) models.add(ResourceLocation.parse(element.getAsString()));
        if (models.isEmpty()) throw new IllegalArgumentException("Empty carriage UI model list");
        String mode = json.has("default_mode") ? json.get("default_mode").getAsString() : "preview";
        if (!mode.equals("preview") && !mode.equals("original")) throw new IllegalArgumentException("Invalid default_mode " + mode);
        return new Page(tab, List.copyOf(models), mode.equals("preview"),
                !json.has("allow_mode_switch") || json.get("allow_mode_switch").getAsBoolean());
    }
}
