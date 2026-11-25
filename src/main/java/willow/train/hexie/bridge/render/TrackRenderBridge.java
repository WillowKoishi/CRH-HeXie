package willow.train.hexie.bridge.render;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackShape;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public interface TrackRenderBridge {
    Class<? extends AbstractTexture> curveBedTextureClass();

    BallastCurveBuffersOperations ballastCurveBuffers();

    interface BallastCurveBuffersOperations {
        void changed(TrackBlockEntity track);
        void removed(TrackBlockEntity track);
        void changed(BezierConnection curve);
        void lightChanged(ClientLevel level, SectionPos pos);
    }

    BallastMeshPointView createBallastMeshPoint(double x, double y, double z);

    BallastModelsOperations ballastModels();

    interface BallastModelsOperations {
        List<BakedQuad> bakeStraight(TrackShape shape, BlockState texture,
                UnaryOperator<BallastMeshPointView> renderPosition);
    }

    BallastTrackModelOperations ballastTrackModel();

    interface BallastTrackModelOperations {
        BakedModel create(BakedModel original);
        ModelProperty<BlockState> texture();
        ModelProperty<List<BakedQuad>> flexiBallast();
        void ballastDataRead(TrackBlockEntity track, boolean packet);
    }

    SlabTrackModelsOperations slabTrackModels();

    interface SlabTrackModelsOperations {
        ModelProperty<Double> flexiPhase();
        boolean applies(BlockState state);
    }

    interface BallastMeshPointView {
        double x();
        double y();
        double z();
    }

    interface CurveBedTextureView {
        TextureAtlasSprite sourceSprite();
        CurveBedTextureView companion(AbstractTexture channelAtlas, TextureAtlasSprite channelSprite);
    }
}
