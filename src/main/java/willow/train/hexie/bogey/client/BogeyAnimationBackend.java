package willow.train.hexie.bogey.client;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Internal client implementation packaged with the active backend. */
public interface BogeyAnimationBackend {
    void register(ResourceLocation bogeyId, Set<ResourceLocation> models);
}
