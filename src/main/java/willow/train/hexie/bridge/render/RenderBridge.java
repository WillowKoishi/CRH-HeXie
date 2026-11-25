package willow.train.hexie.bridge.render;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.sync.SyncBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;

/** Client rendering callbacks and the specialized visual and cache services they use. */
public interface RenderBridge {
    BlockEntityRenderBridge blockEntityRender();

    BogeyRenderBridge bogeyRender();

    CabRenderBridge cabRender();

    CarriageRenderBridge carriageRender();

    DisplayRenderBridge displayRender();

    DoorRenderBridge doorRender();

    TrackRenderBridge trackRender();

    CRH1HeadAnimationUpdatesOperations crh1HeadAnimationUpdates();

    interface CRH1HeadAnimationUpdatesOperations {
        void apply(Level level, SyncBridge.CRH1HeadAnimationPacketView packet);
    }

    CarriageControlPickingView createCarriageControlPicking(AbstractContraptionEntity carrier, Vec3 localFrom,
            Vec3 localTo, float partial);

    CarriageInteractionCoordinatesOperations carriageInteractionCoordinates();

    interface CarriageInteractionCoordinatesOperations {
        float partialTick();
        Vec3 renderEyeOffset(Player player, float partial);
        BlockHitResult pick(Player player, SnapshotCarriageView car);
        AbstractContraptionEntity compatibilityEntity(SnapshotCarriageView car);
        CarriageInteractionCoordinatesPickingRayView toCompatibilityRay(AbstractContraptionEntity visible,
                AbstractContraptionEntity compatibility, Vec3 from, Vec3 to, float partial);
        Vec3 toWorld(AbstractContraptionEntity entity, Vec3 local);
    }

    EntityRenderer<? extends SeatEntity> createHexieSeatRenderer(EntityRendererProvider.Context pContext);

    PartialModelInitOperations partialModelInit();

    interface PartialModelInitOperations {
        PartialModel dt206Frame();
        PartialModel sktb200Frame();
        PartialModel dt206Wheel();
        PartialModel sktb200Wheel();
        PartialModel crh1aMotorFrame();
        PartialModel crh1aMotorWheel();
        PartialModel crh1aTrailerFrame();
        PartialModel crh1aTrailerWheel();
    }

    SS7DWiperVisualView createSS7DWiperVisual(VisualizationContext context,
            BlockEntityViews.SS7DHeadEntityView head, float partialTick);

    SnapshotControlsClientOperations snapshotControlsClient();

    interface SnapshotControlsClientOperations {
        boolean click(SnapshotCarriageView carriage, BlockPos pos, Player player);
        void startConfirmed(SnapshotCarriageView carriage, BlockPos pos);
    }

    interface CarriageControlPickingView {
        boolean replacesNative(BlockPos owner, BlockState state);
        BlockHitResult nearest(BlockHitResult nativeHit);
    }

    interface CarriageInteractionCoordinatesPickingRayView {
        Vec3 from();
        Vec3 to();
        Vec3 localFrom();
        Vec3 localTo();
    }

    interface SS7DWiperVisualView extends CarriageRenderBridge.CarriageBlockVisualView {}
}
