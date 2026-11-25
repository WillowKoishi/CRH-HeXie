package willow.train.hexie.render.bridge;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4fc;
import willow.train.hexie.bridge.render.DoorRenderBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.render.door.CRH1DoorRenderer;
import willow.train.hexie.render.door.CRH2DoorRenderContext;
import willow.train.hexie.render.door.CarriageDoorVisual;

final class DoorRenderServices implements DoorRenderBridge {
    private final CRH1DoorRendererOperations crh1DoorRenderer = new CRH1DoorRendererOperations() {
        @Override public CRH1DoorRendererLightingView installLighting(ClientLevel level, Matrix4fc localToWorld, BlockPos pos) {
            return CRH1DoorRenderer.installLighting(level, localToWorld, pos);
        }
        @Override public void restoreLighting(CRH1DoorRendererLightingView previous) {
            CRH1DoorRenderer.restoreLighting((CRH1DoorRenderer.Lighting) previous);
        }
    };

    private final CRH2DoorRenderContextOperations crh2DoorRenderContext = new CRH2DoorRenderContextOperations() {
        @Override public CRH2DoorRenderContextContextView install(ClientLevel world, Matrix4fc contraptionToWorld, BlockPos blockPos) {
            return CRH2DoorRenderContext.install(world, contraptionToWorld, blockPos);
        }
        @Override public void restore(CRH2DoorRenderContextContextView previous) {
            CRH2DoorRenderContext.restore((CRH2DoorRenderContext.Context) previous);
        }
    };

    @Override public CRH1DoorRendererOperations crh1DoorRenderer() {
        return crh1DoorRenderer;
    }

    @Override public CRH2DoorRenderContextOperations crh2DoorRenderContext() {
        return crh2DoorRenderContext;
    }

    @Override public CarriageDoorVisualView createCarriageDoorVisual(VisualizationContext context, BlockEntityViews.CustomRenderedDoorEntityView door, float partialTick) {
        return CarriageDoorVisual.create(context, door, partialTick);
    }
}
