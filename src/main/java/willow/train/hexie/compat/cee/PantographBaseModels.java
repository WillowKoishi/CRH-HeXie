package willow.train.hexie.compat.cee;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class PantographBaseModels {
    private static final Map<BlockState, BakedModel> MODELS = new HashMap<>();

    private PantographBaseModels() {}

    public static void onBaking(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        MODELS.clear();
        var id = PantographMounting.CEE_PANTOGRAPH;
        if (!BuiltInRegistries.BLOCK.containsKey(id)) return;
        for (var state : BuiltInRegistries.BLOCK.get(id).getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                    (key, original) -> moveToEntityRenderer(state, original));
        }
    }

    public static BakedModel original(BlockState state) { return MODELS.get(state); }

    public static BakedModel moveToEntityRenderer(BlockState state, BakedModel original) {
        MODELS.put(state, original);
        return new BakedModelWrapper<BakedModel>(original) {
            @Override
            public List<BakedQuad> getQuads(BlockState blockState, Direction side, RandomSource random) { return List.of(); }

            @Override
            public List<BakedQuad> getQuads(BlockState blockState, Direction side, RandomSource random, ModelData data, RenderType layer) {
                return List.of();
            }
        };
    }

    public static void render(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = original(state);
        if (model != null) Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), buffers.getBuffer(RenderType.cutout()), state, model, 1, 1, 1, light, overlay);
    }
}
