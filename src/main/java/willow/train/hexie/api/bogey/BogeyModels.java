package willow.train.hexie.api.bogey;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Static models for the existing floor and mechanical top layers. IDs omit models/ and .json.
 * Both use the placed block's coordinates, as in HeXie's existing composite bogeys.
 * The floor stays with the carriage; the top follows the existing height/longitudinal rules.
 * Moving frame, wheel and other fine parts belong to the addon's client animation callback.
 */
public record BogeyModels(ResourceLocation floor, ResourceLocation top) {
    public BogeyModels {
        Objects.requireNonNull(floor, "floor");
        Objects.requireNonNull(top, "top");
    }
}
