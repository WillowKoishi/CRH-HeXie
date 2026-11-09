package willow.train.hexie.api.railway;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Track permissions for the model IDs supplied to HeXie's bogey model catalog.
 * Register once during common setup, before vehicle assembly. This changes only admissible
 * railway IDs; HeXie still owns whole-bogey motion, running constraints, persistence and lifecycle.
 * Addon renderers retain their own wheel, linkage and other fine-part animation calculations.
 */
public final class BogeyRailwayRegistry {
    private static final Map<ResourceLocation, Set<ResourceLocation>> ALLOWED = new ConcurrentHashMap<>();
    private BogeyRailwayRegistry() {}

    /**
     * IDs are HeXie railway registration IDs, not track block IDs or backend track types.
     * Empty means no railway is permitted. Unconfigured models retain their existing behavior.
     * Forward references to other mods' railway declarations are allowed.
     */
    public static void register(ResourceLocation bogeyModelId, Set<ResourceLocation> allowedRailways) {
        var copy = Set.copyOf(Objects.requireNonNull(allowedRailways));
        if (ALLOWED.putIfAbsent(Objects.requireNonNull(bogeyModelId), copy) != null)
            throw new IllegalArgumentException("Bogey railway permissions already registered: " + bogeyModelId);
    }

    public static Optional<Set<ResourceLocation>> allowedRailways(ResourceLocation bogeyModelId) {
        return Optional.ofNullable(ALLOWED.get(Objects.requireNonNull(bogeyModelId)));
    }

    /** Used by the active railway backend, including while restoring and moving saved vehicles. */
    public static boolean allows(String bogeyModelId, ResourceLocation railwayId) {
        Objects.requireNonNull(railwayId);
        var id = ResourceLocation.tryParse(Objects.requireNonNull(bogeyModelId));
        var allowed = id == null ? null : ALLOWED.get(id);
        return allowed == null || allowed.contains(railwayId);
    }
}
