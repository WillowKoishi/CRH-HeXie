package willow.train.hexie.bridge.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.joml.Matrix4fc;
import willow.train.hexie.bridge.world.BlockEntityViews;

public interface BlockEntityRenderBridge {
    boolean isCustomDoorOrSs7dHeadRenderer(BlockEntityRenderer<?> renderer);
    boolean matchesCarriageChildRenderer(BlockEntity entity, BlockEntityRenderer<?> renderer);

    CarriageConsoleVisualView createCarriageConsoleVisual(VisualizationContext context,
            BlockEntityViews.DrivingConsoleEntityView cab);

    DrivingConsoleRendererOperations drivingConsoleRenderer();

    interface DrivingConsoleRendererOperations {
        void registerModels(ModelEvent.RegisterAdditional event);
    }

    GroundStationSignRendererOperations groundStationSignRenderer();

    interface GroundStationSignRendererOperations {
        void renderFace(String[] text, int style, boolean reverse, PoseStack pose, MultiBufferSource buffers,
                int light);
    }

    LacqueredBoardEntityRendererOperations lacqueredBoardEntityRenderer();

    interface LacqueredBoardEntityRendererOperations {
        ResourceLocation sticker();
    }

    LegacyCarriagePartVisualView createLegacyCarriagePartVisual(VisualizationContext context, BlockEntity entity,
            float partialTick);

    interface CarriageConsoleVisualView extends CarriageRenderBridge.CarriageBlockVisualView, DrivingConsoleRendererModelsView, TestDrivingConsoleRendererBoxesView {}

    interface DrivingConsoleRendererModelsView {
        void draw(ModelResourceLocation model, Matrix4fc transform, RenderType layer);
    }

    interface LegacyCarriagePartVisualView extends CarriageRenderBridge.CarriageBlockVisualView {}

    interface TestDrivingConsoleRendererBoxesView {
        void box(float x, float y, float z, float endX, float endY, float endZ, int color);
    }
}
