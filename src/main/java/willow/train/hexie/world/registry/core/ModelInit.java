package willow.train.hexie.world.registry.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelInit {
    private static final RandomSource modelRandom = RandomSource.create();
    private static final Logger MODELS = LoggerFactory.getLogger("model");
    public static final List<ResourceLocation> UNBAKED = new ArrayList<>();
    public static Map<ModelResourceLocation, BakedModel> BAKED = null;

}
