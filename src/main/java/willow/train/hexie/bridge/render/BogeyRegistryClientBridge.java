package willow.train.hexie.bridge.render;

import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.core.api.bogey.BogeyAnimationRegistry;
import willow.train.hexie.core.api.bogey.BogeyAnimator;

public interface BogeyRegistryClientBridge extends BogeyAnimationRegistry.Backend {
    BogeyAnimationsOperations bogeyAnimations();

    interface BogeyAnimationsOperations {
        Optional<? extends BogeyAnimationsRegistrationView> find(ResourceLocation id);
        void freeze(Set<ResourceLocation> commonIds);
    }

    interface BogeyAnimationsRegistrationView {
        Set<ResourceLocation> models();
        BogeyAnimator animator();
    }
}
