package willow.train.hexie.core.api.bogey;

import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Registration of an addon bogey using HeXie's existing running and three-layer conventions.
 * The model is the existing operating/mount declaration, not a list of animated model parts.
 * Fine-part pivots and animation calculations remain in the addon.
 */
public record BogeyDefinition(ResourceLocation blockId, String displayName,
                              BogeyModel model, Set<ResourceLocation> allowedRailways,
                              BogeyModels models) {
    public BogeyDefinition {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) throw new IllegalArgumentException("Bogey display name must not be blank");
        Objects.requireNonNull(model, "model");
        allowedRailways = Set.copyOf(Objects.requireNonNull(allowedRailways, "allowedRailways"));
        Objects.requireNonNull(models, "models");
    }
}
