package willow.train.hexie.render.init;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import java.util.*;
import java.util.function.Supplier;

// split from willow/train/hexie/init/client/HeXieClientInit.java, lines 62-74
final class ClientModelLayers {
    private static final Map<ModelLayerLocation, Supplier<LayerDefinition>> modelLayers =
            new HashMap<>();

    public static void registerModelLayer(
            ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
        modelLayers.put(layer, definition);
    }

    public static void onModelLayerRegistration(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        modelLayers.forEach(event::registerLayerDefinition);
        modelLayers.clear();
    }
}
