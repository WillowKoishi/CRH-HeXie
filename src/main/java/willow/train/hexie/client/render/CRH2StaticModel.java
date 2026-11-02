package willow.train.hexie.client.render;

import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Terrain geometry is emitted once, into its destination sections. Item/virtual models stay available. */
final class CRH2StaticModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> SECTION_GEOMETRY = new ModelProperty<>();
    CRH2StaticModel(BakedModel original) { super(original); }

    @Override public ModelData getModelData(BlockAndTintGetter world, BlockPos pos, BlockState state, ModelData data) {
        var inherited = super.getModelData(world, pos, state, data);
        // Only suppress the terrain pipelines that invoke AddSectionGeometryEvent.
        // Create's VirtualRenderWorld, previews and item rendering retain the original model.
        boolean terrain = world instanceof net.minecraft.client.multiplayer.ClientLevel
                || world instanceof net.minecraft.client.renderer.chunk.RenderChunkRegion
                || world.getClass().getName().equals("net.caffeinemc.mods.sodium.client.world.LevelSlice");
        return inherited.derive().with(SECTION_GEOMETRY, terrain).build();
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                                             ModelData data, RenderType layer) {
        if (Boolean.TRUE.equals(data.get(SECTION_GEOMETRY))) return List.of();
        return super.getQuads(state, side, random, data, layer);
    }
}
