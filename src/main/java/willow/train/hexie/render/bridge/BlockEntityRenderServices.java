package willow.train.hexie.render.bridge;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import willow.train.hexie.bridge.render.BlockEntityRenderBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.render.blockentity.console.CarriageConsoleVisual;
import willow.train.hexie.render.blockentity.console.DrivingConsoleRenderer;
import willow.train.hexie.render.blockentity.part.LegacyCarriagePartVisual;
import willow.train.hexie.render.blockentity.sign.GroundStationSignRenderer;
import willow.train.hexie.render.blockentity.sign.LacqueredBoardEntityRenderer;
import willow.train.hexie.render.door.CustomRenderedDoorRenderer;
import willow.train.hexie.render.ss7d.SS7DHeadRenderer;

final class BlockEntityRenderServices implements BlockEntityRenderBridge {
    @Override public boolean isCustomDoorOrSs7dHeadRenderer(BlockEntityRenderer<?> renderer) {
        return renderer != null && (renderer.getClass() == CustomRenderedDoorRenderer.class
                || renderer.getClass() == SS7DHeadRenderer.class);
    }

    @Override public boolean matchesCarriageChildRenderer(BlockEntity entity, BlockEntityRenderer<?> renderer) {
        if (entity instanceof BlockEntityViews.DrivingConsoleEntityView)
            return renderer instanceof DrivingConsoleRenderer;
        if (!isCustomDoorOrSs7dHeadRenderer(renderer)) return false;
        return renderer instanceof CustomRenderedDoorRenderer
                ? entity instanceof BlockEntityViews.CustomRenderedDoorEntityView
                : entity instanceof BlockEntityViews.SS7DHeadEntityView;
    }

    private final DrivingConsoleRendererOperations drivingConsoleRenderer = DrivingConsoleRenderer::registerModels;

    private final GroundStationSignRendererOperations groundStationSignRenderer = GroundStationSignRenderer::renderFace;

    private final LacqueredBoardEntityRendererOperations lacqueredBoardEntityRenderer = new LacqueredBoardEntityRendererOperations() {
        @Override public ResourceLocation sticker() {
            return LacqueredBoardEntityRenderer.STICKER;
        }
    };

    @Override public CarriageConsoleVisualView createCarriageConsoleVisual(VisualizationContext context, BlockEntityViews.DrivingConsoleEntityView cab) {
        return CarriageConsoleVisual.create(context, cab);
    }

    @Override public DrivingConsoleRendererOperations drivingConsoleRenderer() {
        return drivingConsoleRenderer;
    }

    @Override public GroundStationSignRendererOperations groundStationSignRenderer() {
        return groundStationSignRenderer;
    }

    @Override public LacqueredBoardEntityRendererOperations lacqueredBoardEntityRenderer() {
        return lacqueredBoardEntityRenderer;
    }

    @Override public LegacyCarriagePartVisualView createLegacyCarriagePartVisual(VisualizationContext context, BlockEntity entity, float partialTick) {
        return LegacyCarriagePartVisual.create(context, entity, partialTick);
    }
}
