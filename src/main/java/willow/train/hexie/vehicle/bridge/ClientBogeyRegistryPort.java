package willow.train.hexie.vehicle.bridge;

import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import willow.train.hexie.bridge.render.BogeyRegistryClientBridge;
import willow.train.hexie.core.api.bogey.BogeyAnimator;
import willow.train.hexie.vehicle.bogey.client.BogeyAnimations;

final class ClientBogeyRegistryPort implements BogeyRegistryClientBridge {
    private final BogeyAnimationsOperations bogeyAnimations = new BogeyAnimationsOperations() {
        @Override
        public Optional<? extends BogeyAnimationsRegistrationView> find(ResourceLocation id) {
            return BogeyAnimations.find(id);
        }

        @Override
        public void freeze(Set<ResourceLocation> commonIds) {
            BogeyAnimations.freeze(commonIds);
        }
    };

    @Override
    public BogeyAnimationsOperations bogeyAnimations() {
        return bogeyAnimations;
    }

    @Override
    public void register(ResourceLocation id, Set<ResourceLocation> models, BogeyAnimator callback) {
        BogeyAnimations.register(id, models, callback);
    }
}
