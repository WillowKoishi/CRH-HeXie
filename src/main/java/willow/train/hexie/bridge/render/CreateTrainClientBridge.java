package willow.train.hexie.bridge.render;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

public interface CreateTrainClientBridge {
    CreateAddonBogeyClientOperations createAddonBogeyClient();

    interface CreateAddonBogeyClientOperations {
        void registerAdditional(ModelEvent.RegisterAdditional event);
        void registerRenderers(EntityRenderersEvent.RegisterRenderers event);
    }

    CreateAddonBogeyRendererOperations createAddonBogeyRenderer();

    interface CreateAddonBogeyRendererOperations {
        CreateAddonBogeyRendererSampleScopeView snapshotSample(double wheelAngle, double speedBlocksPerTick,
                float partialTick);
    }

    CreateBogeyAnimationBackendOperations createBogeyAnimationBackend();

    interface CreateBogeyAnimationBackendOperations {
        void register(ResourceLocation bogeyId, Set<ResourceLocation> models);
    }

    interface CreateAddonBogeyRendererSampleScopeView extends AutoCloseable {
        void close();
    }
}
