package willow.train.hexie.client.render.bogey;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import willow.train.hexie.blocks.bogeys.*;

/** Existing top geometry becomes the adjustable middle layer; the floor has no new geometry. */
public final class BogeyTopModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Vec3> OFFSET = new ModelProperty<>();
    public BogeyTopModel(BakedModel original) { super(original); }
    public static void register(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (!(block instanceof HexieBogeyBlock<?>)) continue;
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var key = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
                event.getModels().computeIfPresent(key, (ignored, model) -> new BogeyTopModel(model));
            }
        }
    }
    @Override public ModelData getModelData(BlockAndTintGetter world, BlockPos pos, BlockState state, ModelData data) {
        var inherited = super.getModelData(world, pos, state, data);
        Vec3 offset = Vec3.ZERO;
        if (world.getBlockEntity(pos) instanceof HexieBogeyBlockEntity be) {
            offset = BogeyFloorData.displacement(state, BogeyFloorData.longitudinal(be.getBogeyData()));
            if (!be.isCarriageView()) offset = offset.add(0, BogeyFloorData.offset(BogeyFloorData.get(be)), 0);
        }
        return inherited.derive().with(OFFSET, offset).build();
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        var offset = data.get(OFFSET);
        if (state == null || offset == null || offset.equals(Vec3.ZERO)) return super.getQuads(state, side, random, data, layer);
        if (side != null) return List.of();
        var quads = new ArrayList<>(super.getQuads(state, null, random, data, layer));
        for (var face : Direction.values()) quads.addAll(super.getQuads(state, face, random, data, layer));
        return quads.stream().map(quad -> {
            int[] vertices = quad.getVertices().clone();
            int stride = vertices.length / 4;
            for (int i = 0; i < 4; i++) {
                int p = i * stride;
                vertices[p] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[p]) + (float) offset.x);
                vertices[p+1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[p+1]) + (float) offset.y);
                vertices[p+2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[p+2]) + (float) offset.z);
            }
            return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
        }).toList();
    }
}
