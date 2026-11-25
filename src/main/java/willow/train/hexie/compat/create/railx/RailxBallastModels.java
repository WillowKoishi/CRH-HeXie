package willow.train.hexie.compat.create.railx;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackShape;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.SinglePosVirtualBlockGetter;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import java.util.List;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.WorldBlockBridge.BallastDataView;
import willow.train.hexie.compat.create.track.TrackBallast;

/** Bind the existing painted track model to RailX's two cached straight-track render paths. */
public final class RailxBallastModels {
    private record Key(BlockState track, BlockState texture, int slabPhase, Quaterniondc rotation) {}

    private static final SuperByteBufferCache.Compartment<Key> BUFFERS = new SuperByteBufferCache.Compartment<>();
    private static final RendererReloadCache<BlockState, Cache<Key, Model>> MODELS = new RendererReloadCache<>(
            state -> CacheBuilder.newBuilder().maximumSize(512).build());

    static {
        SuperByteBufferCache.getInstance().registerCompartment(BUFFERS, 20 * 60);
    }

    private RailxBallastModels() {}

    public static boolean applies(BlockState state) {
        return TrackBallast.enabled(state) || HexieBridges.Client.trackRender().slabTrackModels().applies(state);
    }

    public static SuperByteBuffer buffer(BlockState state, TrackBlockEntity track, Quaterniondc rotation) {
        var key = key(state, track, rotation);
        return SuperByteBufferCache.getInstance().get(BUFFERS, key,
                () -> SuperBufferFactory.getInstance().createForBlock(boundModel(key), state));
    }

    public static Model model(BlockState state, TrackBlockEntity track, Quaterniondc rotation) {
        var key = key(state, track, rotation);
        return MODELS.get(state).asMap().computeIfAbsent(key, ignored -> new BakedModelBuilder(boundModel(key))
                .level(SinglePosVirtualBlockGetter.createFullDark().blockState(key.track())).build());
    }

    private static Key key(BlockState state, TrackBlockEntity track, Quaterniondc rotation) {
        if (HexieBridges.Client.trackRender().slabTrackModels().applies(state)) {
            var tangent = rotation.transform(new Vector3d(1, 0, 0));
            var pos = track.getBlockPos();
            // RailX rotates the XO model about (.5, .125, .5). Its start is half a metre before that pivot.
            double start = tangent.dot(pos.getX() + .5, pos.getY() + .125, pos.getZ() + .5) - .5;
            // Bound the number of cached GPU models even for freely rotated tracks.
            int phase = Math.floorMod(Math.round(start * 64), 7 * 64);
            return new Key(state, Blocks.SMOOTH_STONE.defaultBlockState(), phase, null);
        }
        return new Key(state, ((BallastDataView) track).hexie$getBallast(null), 0, rotation);
    }

    private static BakedModel boundModel(Key key) {
        var original = Minecraft.getInstance().getBlockRenderer().getBlockModel(key.track());
        var data = HexieBridges.Client.trackRender().slabTrackModels().applies(key.track())
                ? ModelData.builder().with(HexieBridges.Client.trackRender().slabTrackModels().flexiPhase(), key.slabPhase() / 64.0).build()
                : ModelData.builder().with(HexieBridges.Client.trackRender().ballastTrackModel().flexiBallast(),
                        HexieBridges.Client.trackRender().ballastModels().bakeStraight(TrackShape.XO, key.texture(), p -> {
                            // RailX applies this pivot rotation after baking in both renderers.
                            var rotated = key.rotation().transform(new Vector3d(p.x() - .5, p.y() - .125, p.z() - .5));
                            return HexieBridges.Client.trackRender().createBallastMeshPoint(rotated.x + .5, rotated.y + .125, rotated.z + .5);
                        })).build();
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
