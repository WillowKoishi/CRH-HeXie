package willow.train.hexie.bridge.render;

import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

public interface WorldRegistryClientBridge {
    ModelInitOperations modelInit();

    interface ModelInitOperations {
        List<ResourceLocation> unbaked();
        Map<ModelResourceLocation, BakedModel> baked();

        /** Providers with a replaceable registry can retain the event's map directly. */
        default void setBaked(Map<ModelResourceLocation, BakedModel> models) {
            var current = baked();
            if (current != null && current != models) {
                current.clear();
                current.putAll(models);
            }
        }
    }
}
