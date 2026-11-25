package willow.train.hexie.bridge.render;

import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface SableClientBridge {
    SableRenderEmbeddingOperations sableRenderEmbedding();

    interface SableRenderEmbeddingOperations {
        SableRenderEmbeddingHandleView find(Level level, BlockPos owner);
    }

    interface SableRenderEmbeddingBindingView {
        VisualEmbedding embedding();
        boolean update(float partialTick);
        void delete();
    }

    interface SableRenderEmbeddingHandleView {
        boolean removed();
        SableRenderEmbeddingBindingView create(VisualizationContext parent, float partialTick);
    }
}
