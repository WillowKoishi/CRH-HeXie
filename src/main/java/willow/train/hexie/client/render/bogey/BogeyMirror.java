package willow.train.hexie.client.render.bogey;

import java.util.List;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import net.createmod.catnip.render.SuperBufferFactory;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Reflect each face and reverse its winding so it remains visible from outside. */
final class BogeyMirror extends BakedModelWrapper<BakedModel> {
    private BogeyMirror(PartialModel source) { super(source.get()); }

    static final RendererReloadCache<PartialModel, SuperByteBuffer> BUFFERS = new RendererReloadCache<>(part ->
            SuperBufferFactory.getInstance().createForBlock(new BogeyMirror(part), Blocks.AIR.defaultBlockState()));
    static final RendererReloadCache<PartialModel, Model> MODELS = new RendererReloadCache<>(part ->
            new BakedModelBuilder(new BogeyMirror(part)).materialFunc((type, shaded, ao) -> {
                var material = ModelUtil.getMaterial(type, shaded, ao);
                return material == null ? null : SimpleMaterial.builderOf(material).light(LightShaders.SMOOTH)
                        .cardinalLightingMode(shaded ? CardinalLightingMode.ENTITY : CardinalLightingMode.OFF).build();
            }).build());

    static Direction face(Direction face, Direction.Axis axis) {
        return face != null && face.getAxis() == axis ? face.getOpposite() : face;
    }

    static BakedQuad reflect(BakedQuad quad, Direction.Axis axis, float twiceCenter) {
        int component = axis == Direction.Axis.X ? 0 : 2;
        int[] src = quad.getVertices(), dst = new int[src.length];
        int stride = src.length / 4;
        int shift = component * 8;
        for (int i = 0; i < 4; i++) {
            int p = i * stride;
            System.arraycopy(src, (3 - i) * stride, dst, p, stride);
            dst[p + component] = Float.floatToRawIntBits(twiceCenter - Float.intBitsToFloat(dst[p + component]));
            int normal = dst[p+7];
            int reflected = -(byte)(normal >>> shift);
            dst[p+7] = (normal & ~(255 << shift)) | ((reflected & 255) << shift);
        }
        return new BakedQuad(dst, quad.getTintIndex(), face(quad.getDirection(), axis), quad.getSprite(),
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                                              ModelData data, RenderType layer) {
        return originalModel.getQuads(state, face(side, Direction.Axis.Z), random, data, layer).stream()
                .map(q -> reflect(q, Direction.Axis.Z, 0)).toList();
    }
}
