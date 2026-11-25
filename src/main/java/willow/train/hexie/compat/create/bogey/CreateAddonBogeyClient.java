package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.BogeyBlockEntityRenderer;
import java.util.stream.Collectors;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.bogey.BogeyHandle;
import willow.train.hexie.core.api.bogey.BogeyRegistry;

/** Client registrations for the backend-owned block entities of declaratively registered bogeys. */
public final class CreateAddonBogeyClient {
    private CreateAddonBogeyClient() {}

    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        HexieBridges.Client.bogeyRegistryClient().bogeyAnimations().freeze(BogeyRegistry.entries().stream().map(BogeyHandle::id).collect(Collectors.toSet()));
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        CreateBogeyBackend.blockEntityTypes().forEach(type ->
                event.registerBlockEntityRenderer(type, BogeyBlockEntityRenderer::new));
    }
}
