package willow.train.hexie.compat.create.railx.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.SinglePosVirtualBlockGetter;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import net.createmod.catnip.render.SuperBufferFactory;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.render.SuperByteBufferCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.track.standard.BallastData;
import willow.train.hexie.blocks.track.standard.TrackBallast;
import willow.train.hexie.client.render.track.BallastTrackModel;

import java.util.List;

/** Bind the existing painted track model to RailX's two cached straight-track render paths. */
@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RailxBallastModels {
    private record Key(BlockState track, BlockState texture) {}

    private static final SuperByteBufferCache.Compartment<Key> BUFFERS = new SuperByteBufferCache.Compartment<>();
    private static final RendererReloadCache<Key, Model> MODELS = new RendererReloadCache<>(key ->
            new BakedModelBuilder(boundModel(key))
                    .level(SinglePosVirtualBlockGetter.createFullDark().blockState(key.track()))
                    .build());

    static {
        SuperByteBufferCache.getInstance().registerCompartment(BUFFERS);
    }

    private RailxBallastModels() {}

    public static boolean applies(BlockState state) {
        return TrackBallast.enabled(state);
    }

    public static SuperByteBuffer buffer(BlockState state, TrackBlockEntity track) {
        var key = key(state, track);
        return SuperByteBufferCache.getInstance().get(BUFFERS, key,
                () -> SuperBufferFactory.getInstance().createForBlock(boundModel(key), state));
    }

    public static Model model(BlockState state, TrackBlockEntity track) {
        return MODELS.get(key(state, track));
    }

    private static Key key(BlockState state, TrackBlockEntity track) {
        return new Key(state, ((BallastData) track).hexie$getBallast(null));
    }

    private static BakedModel boundModel(Key key) {
        var original = Minecraft.getInstance().getBlockRenderer().getBlockModel(key.track());
        var data = ModelData.builder().with(BallastTrackModel.TEXTURE, key.texture()).build();
        return new BoundModel(original, key.track(), data);
    }

    @SubscribeEvent
    public static void reload(ModelEvent.BakingCompleted event) {
        MODELS.clear();
        RenderSystem.recordRenderCall(() -> SuperByteBufferCache.getInstance().invalidate(BUFFERS));
    }

    private static final class BoundModel extends BakedModelWrapper<BakedModel> {
        private final BlockState state;
        private final ModelData data;

        private BoundModel(BakedModel original, BlockState state, ModelData data) {
            super(original);
            this.state = state;
            this.data = data;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState ignored, Direction side, RandomSource random) {
            return originalModel.getQuads(state, side, random, data, null);
        }

        @Override
        public List<BakedQuad> getQuads(BlockState ignored, Direction side, RandomSource random,
                                       ModelData ignoredData, RenderType layer) {
            return originalModel.getQuads(state, side, random, data, layer);
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter world, BlockPos pos, BlockState ignored, ModelData ignoredData) {
            return data;
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState ignored, RandomSource random, ModelData ignoredData) {
            return originalModel.getRenderTypes(state, random, data);
        }
    }
}
