package willow.train.hexie.api.bogey.client;

import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Draws a declared part with the add-on's model-local transform, in either rendering path.
 * HeXie has already applied the whole bogey pose, existing model-origin offset and reversal.
 * Translations are in blocks; do not apply those whole-bogey transforms again.
 */
@FunctionalInterface
public interface BogeyPartRenderer {
    void draw(ResourceLocation model, Matrix4fc transform);

    default void draw(ResourceLocation model) { draw(model, new Matrix4f()); }
}
