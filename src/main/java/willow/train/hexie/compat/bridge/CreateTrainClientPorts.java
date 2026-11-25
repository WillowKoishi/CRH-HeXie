package willow.train.hexie.compat.bridge;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import willow.train.hexie.bridge.render.CreateTrainClientBridge;
import willow.train.hexie.compat.create.bogey.CreateAddonBogeyClient;
import willow.train.hexie.compat.create.bogey.CreateAddonBogeyRenderer;
import willow.train.hexie.compat.create.bogey.CreateBogeyAnimationBackend;

final class CreateTrainClientPorts implements CreateTrainClientBridge {
    private final CreateAddonBogeyClientOperations addonBogeyClient = new CreateAddonBogeyClientOperations() {
        @Override public void registerAdditional(ModelEvent.RegisterAdditional event) {
            CreateAddonBogeyClient.registerAdditional(event);
        }

        @Override public void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            CreateAddonBogeyClient.registerRenderers(event);
        }
    };

    @Override public CreateAddonBogeyClientOperations createAddonBogeyClient() {
        return addonBogeyClient;
    }

    @Override public CreateAddonBogeyRendererOperations createAddonBogeyRenderer() {
        return CreateAddonBogeyRenderer::snapshotSample;
    }

    @Override public CreateBogeyAnimationBackendOperations createBogeyAnimationBackend() {
        return CreateBogeyAnimationBackend::register;
    }
}
