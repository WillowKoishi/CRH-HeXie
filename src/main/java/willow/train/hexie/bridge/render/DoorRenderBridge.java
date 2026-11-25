package willow.train.hexie.bridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4fc;
import willow.train.hexie.bridge.world.BlockEntityViews;

public interface DoorRenderBridge {
    CRH1DoorRendererOperations crh1DoorRenderer();

    interface CRH1DoorRendererOperations {
        CRH1DoorRendererLightingView installLighting(ClientLevel level, Matrix4fc localToWorld, BlockPos pos);
        void restoreLighting(CRH1DoorRendererLightingView previous);
    }

    CRH2DoorRenderContextOperations crh2DoorRenderContext();

    interface CRH2DoorRenderContextOperations {
        CRH2DoorRenderContextContextView install(ClientLevel world, Matrix4fc contraptionToWorld,
                BlockPos blockPos);
        void restore(CRH2DoorRenderContextContextView previous);
    }

    CarriageDoorVisualView createCarriageDoorVisual(VisualizationContext context,
            BlockEntityViews.CustomRenderedDoorEntityView door, float partialTick);

    interface CRH2DoorRenderContextContextView {
        ClientLevel level();
        Matrix4fc localToWorld();
    }

    interface CRH1DoorRendererLightingView {
        ClientLevel level();
        Matrix4fc localToWorld();
    }

    interface CarriageDoorVisualView extends CarriageRenderBridge.CarriageBlockVisualView, CustomRenderedDoorRendererPartRendererView {}

    interface CustomRenderedDoorRendererPartRendererView {
        void draw(PartialModel model, RenderType layer, PoseStack transform);
    }
}
