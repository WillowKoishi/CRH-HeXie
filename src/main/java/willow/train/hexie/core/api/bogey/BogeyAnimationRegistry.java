package willow.train.hexie.core.api.bogey;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.core.ServiceSlot;

/** Client-only registrations, made during mod construction before the first model bake. */
public final class BogeyAnimationRegistry {
    public interface Backend {
        void register(ResourceLocation bogeyId, Set<ResourceLocation> models, BogeyAnimator callback);
    }

    private static final ServiceSlot<Backend> BACKEND = new ServiceSlot<>("BogeyAnimationRegistry");

    public static void registerBackend(Backend backend) { BACKEND.register(backend); }

    private BogeyAnimationRegistry() {}

    /**
     * Uses the same ID as the common bogey registration. Models are declared only for pre-baking
     * and draw validation; all part animation algorithms and attachment data belong to the callback.
     * The callback can run repeatedly or concurrently for ordinary/instanced rendering and must not
     * advance simulation state or retain the supplied renderer after it returns.
     */
    public static void register(ResourceLocation bogeyId, Set<ResourceLocation> models, BogeyAnimator callback) {
        BACKEND.get().register(bogeyId, models, callback);
    }
}
