package willow.train.hexie.render.bridge;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackShape;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import willow.train.hexie.bridge.render.TrackRenderBridge;
import willow.train.hexie.render.track.BallastCurveBuffers;
import willow.train.hexie.render.track.BallastMesh;
import willow.train.hexie.render.track.BallastModels;
import willow.train.hexie.render.track.BallastRegionRendering;
import willow.train.hexie.render.track.BallastTrackModel;
import willow.train.hexie.render.track.CurveBedTexture;
import willow.train.hexie.render.track.SlabTrackModels;

final class TrackRenderServices implements TrackRenderBridge {
    @Override public Class<? extends AbstractTexture> curveBedTextureClass() {
        return CurveBedTexture.class;
    }

    private final BallastCurveBuffersOperations ballastCurveBuffers = new BallastCurveBuffersOperations() {
        @Override public void changed(TrackBlockEntity track) {
            BallastCurveBuffers.changed(track);
        }
        @Override public void removed(TrackBlockEntity track) {
            BallastCurveBuffers.removed(track);
        }
        @Override public void changed(BezierConnection curve) {
            BallastCurveBuffers.changed(curve);
        }
        @Override public void lightChanged(ClientLevel level, SectionPos pos) {
            BallastRegionRendering.lightChanged(level, pos);
        }
    };

    private final BallastModelsOperations ballastModels = new BallastModelsOperations() {
        @Override public List<BakedQuad> bakeStraight(TrackShape shape, BlockState texture, UnaryOperator<BallastMeshPointView> renderPosition) {
            return BallastModels.bakeStraight(shape, texture, point -> {
                var result = renderPosition.apply(point);
                return new BallastMesh.Point(result.x(), result.y(), result.z());
            });
        }
    };

    private final BallastTrackModelOperations ballastTrackModel = new BallastTrackModelOperations() {
        @Override public BakedModel create(BakedModel original) {
            return BallastTrackModel.create(original);
        }
        @Override public ModelProperty<BlockState> texture() {
            return BallastTrackModel.TEXTURE;
        }
        @Override public ModelProperty<List<BakedQuad>> flexiBallast() {
            return BallastTrackModel.FLEXI_BALLAST;
        }
        @Override public void ballastDataRead(TrackBlockEntity track, boolean packet) {
            BallastTrackModel.ballastDataRead(track, packet);
        }
    };

    private final SlabTrackModelsOperations slabTrackModels = new SlabTrackModelsOperations() {
        @Override public ModelProperty<Double> flexiPhase() {
            return SlabTrackModels.FLEXI_PHASE;
        }
        @Override public boolean applies(BlockState state) {
            return SlabTrackModels.applies(state);
        }
    };

    @Override public BallastCurveBuffersOperations ballastCurveBuffers() {
        return ballastCurveBuffers;
    }

    @Override public BallastMeshPointView createBallastMeshPoint(double x, double y, double z) {
        return new BallastMesh.Point(x, y, z);
    }

    @Override public BallastModelsOperations ballastModels() {
        return ballastModels;
    }

    @Override public BallastTrackModelOperations ballastTrackModel() {
        return ballastTrackModel;
    }

    @Override public SlabTrackModelsOperations slabTrackModels() {
        return slabTrackModels;
    }
}
