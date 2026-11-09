package willow.train.hexie.client.render.bogey;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import willow.train.hexie.blocks.bogeys.*;
import willow.train.hexie.blocks.bogeys.CRH.CRHBogeyBlock;
import willow.train.hexie.compat.create.bogey.RegisteredBogeyBlock;
import willow.train.hexie.init.track.TrackMaterialInit;

/** The bolster keeps its rail-height reference. Only the floor follows configured floor height. */
public final class BogeyTopModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> REVERSED = new ModelProperty<>();
    private static final ModelProperty<Vec3> OFFSET = new ModelProperty<>();
    private static final ModelProperty<Vec3> FLOOR_OFFSET = new ModelProperty<>();
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
        if (world.getBlockEntity(pos) instanceof HexieBogeyBlockEntity be)
            return withBogeyData(inherited, state, be.getBogeyData(), be.isCarriageView());
        return withBogeyData(inherited, state, null, false);
    }

    /** Supplies the same bolster mesh for a head-owned bogey without a world block entity. */
    public static ModelData withBogeyData(ModelData inherited, BlockState state, CompoundTag data, boolean carriageView) {
        boolean reversed = data != null && data.getBoolean(BogeyFloorData.REVERSED);
        Vec3 offset = restOffset(state);
        Vec3 floorOffset = Vec3.ZERO;
        if (data != null) {
            offset = offset.add(BogeyFloorData.displacement(state, BogeyFloorData.longitudinal(data)));
            double height = BogeyFloorData.offset(BogeyFloorData.heightFromBogeyData(data));
            // Snapshot body poses already include this height; their mechanical top must compensate it.
            // Original non-EMU tops rise with the rail; EMU tops were already calibrated to that rail.
            if (carriageView) offset = offset.add(0, -height, 0);
            else floorOffset = new Vec3(0, height, 0);
        }
        return inherited.derive().with(OFFSET, offset).with(FLOOR_OFFSET, floorOffset).with(REVERSED, reversed).build();
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }
    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        var offset = data.get(OFFSET);
        if (offset == null) offset = restOffset(state);
        if (hasSeparateFloor(state)
                && originalModel instanceof net.neoforged.neoforge.client.model.CompositeModel.Baked assembly) {
            var shift = offset == null ? Vec3.ZERO : offset;
            var result = new ArrayList<>(bolsterQuads(assembly.getPart("bolster"), state, side, random, data, layer, shift));
            if (!state.getValue(HexieBogeyBlock.HEAD_MOUNTED)) {
                result.addAll(partQuads(assembly.getPart("floor"), state, side, random, data, layer, data.get(FLOOR_OFFSET)));
            }
            return result;
        }
        return bolsterQuads(originalModel, state, side, random, data, layer, offset);
    }

    private static Vec3 restOffset(BlockState state) {
        return state != null && !hasSeparateFloor(state)
                ? new Vec3(0, TrackMaterialInit.RAIL_HEIGHT_INCREASE, 0) : Vec3.ZERO;
    }

    private static boolean hasSeparateFloor(BlockState state) {
        return state != null && (state.getBlock() instanceof CRHBogeyBlock
                || state.getBlock() instanceof RegisteredBogeyBlock);
    }

    private static List<BakedQuad> bolsterQuads(BakedModel model, BlockState state, Direction side, RandomSource random,
                                                ModelData data, RenderType layer, Vec3 shift) {
        if (model == null || (state != null && layer != null && !model.getRenderTypes(state, random, data).contains(layer))) return List.of();
        if (state == null || !Boolean.TRUE.equals(data.get(REVERSED)))
            return shifted(model, state, side, random, data, layer, shift);
        var axis = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS);
        float twiceCenter = 1 + 2 * (float)axis.choose(shift.x, shift.y, shift.z);
        return shifted(model, state, BogeyMirror.face(side, axis), random, data, layer, shift).stream()
                .map(q -> BogeyMirror.reflect(q, axis, twiceCenter)).toList();
    }

    private static List<BakedQuad> partQuads(BakedModel part, BlockState state, Direction side, RandomSource random,
                                             ModelData data, RenderType layer, Vec3 shift) {
        if (part == null || (layer != null && !part.getRenderTypes(state, random, data).contains(layer))) return List.of();
        return shifted(part, state, side, random, data, layer, shift);
    }

    private static List<BakedQuad> shifted(BakedModel model, BlockState state, Direction side, RandomSource random,
                                           ModelData data, RenderType layer, Vec3 offset) {
        if (state == null || offset == null || offset.equals(Vec3.ZERO)) return model.getQuads(state, side, random, data, layer);
        if (side != null) return List.of();
        var quads = new ArrayList<>(model.getQuads(state, null, random, data, layer));
        for (var face : Direction.values()) quads.addAll(model.getQuads(state, face, random, data, layer));
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
