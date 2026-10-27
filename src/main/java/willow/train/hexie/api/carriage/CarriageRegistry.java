package willow.train.hexie.api.carriage;

import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client asset declarations. Register during mod construction, before opening the browser.
 * The directory is relative to assets/<namespace>/ and contains carriage.json and structures.
 * Resource-pack declarations in carriage_ui/types/*.json can override these registrations.
 */
public final class CarriageRegistry {
    private static final Map<ResourceLocation, ResourceLocation> ENTRIES = new LinkedHashMap<>();

    private CarriageRegistry() {}

    public static synchronized void register(ResourceLocation model, ResourceLocation assetDirectory) {
        if (ENTRIES.putIfAbsent(model, assetDirectory) != null)
            throw new IllegalArgumentException("Duplicate carriage model: " + model);
    }

    public static synchronized Map<ResourceLocation, ResourceLocation> entries() {
        return Map.copyOf(ENTRIES);
    }
}
