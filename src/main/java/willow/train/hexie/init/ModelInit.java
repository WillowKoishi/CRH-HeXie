package willow.train.hexie.init;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModelInit {
    private static final RandomSource modelRandom = RandomSource.create();
    private static final Logger MODELS = LoggerFactory.getLogger("model");
    public static final List<ResourceLocation> UNBAKED = new ArrayList<>();
    public static Map<net.minecraft.client.resources.model.ModelResourceLocation, BakedModel> BAKED = null;

    public static void register(ResourceLocation location) {
        UNBAKED.add(location);
    }

    public static BakedModel getModel(ResourceLocation location) {
        if (BAKED == null) {
            MODELS.error("Called too early!");
            return null;
        }
        return BAKED.getOrDefault(net.minecraft.client.resources.model.ModelResourceLocation.standalone(location), null);
    }

    public static void render(PoseStack pose, BakedModel model, VertexConsumer buffer, int light, int overlay) {
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        renderer.renderQuadList(pose, buffer, model.getQuads(null, null, modelRandom, ModelData.EMPTY, null), ItemStack.EMPTY, light, overlay);
    }
}
