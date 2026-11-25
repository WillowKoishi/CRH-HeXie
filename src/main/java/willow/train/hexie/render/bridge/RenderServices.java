package willow.train.hexie.render.bridge;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.render.BlockEntityRenderBridge;
import willow.train.hexie.bridge.render.BogeyRenderBridge;
import willow.train.hexie.bridge.render.CabRenderBridge;
import willow.train.hexie.bridge.render.CarriageRenderBridge;
import willow.train.hexie.bridge.render.DisplayRenderBridge;
import willow.train.hexie.bridge.render.DoorRenderBridge;
import willow.train.hexie.bridge.render.RenderBridge;
import willow.train.hexie.bridge.render.TrackRenderBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.render.coupler.CRH1HeadAnimationUpdates;
import willow.train.hexie.render.entity.HexieSeatRenderer;
import willow.train.hexie.render.init.CarriageControlPicking;
import willow.train.hexie.render.init.CarriageInteractionCoordinates;
import willow.train.hexie.render.init.PartialModelInit;
import willow.train.hexie.render.init.SnapshotControlsClient;
import willow.train.hexie.render.ss7d.SS7DWiperVisual;

final class RenderServices implements RenderBridge {
    private final BlockEntityRenderBridge blockEntityRender = new BlockEntityRenderServices();

    private final BogeyRenderBridge bogeyRender = new BogeyRenderServices();

    private final CabRenderBridge cabRender = new CabRenderServices();

    private final CarriageRenderBridge carriageRender = new CarriageRenderServices();

    private final DisplayRenderBridge displayRender = new DisplayRenderServices();

    private final DoorRenderBridge doorRender = new DoorRenderServices();

    private final TrackRenderBridge trackRender = new TrackRenderServices();

    private final CRH1HeadAnimationUpdatesOperations crh1HeadAnimationUpdates = CRH1HeadAnimationUpdates::apply;

    private final CarriageInteractionCoordinatesOperations carriageInteractionCoordinates = new CarriageInteractionCoordinatesOperations() {
        @Override public float partialTick() {
            return CarriageInteractionCoordinates.partialTick();
        }
        @Override public Vec3 renderEyeOffset(Player player, float partial) {
            return CarriageInteractionCoordinates.renderEyeOffset(player, partial);
        }
        @Override public BlockHitResult pick(Player player, SnapshotCarriageView car) {
            return CarriageInteractionCoordinates.pick(player, car);
        }
        @Override public AbstractContraptionEntity compatibilityEntity(SnapshotCarriageView car) {
            return CarriageInteractionCoordinates.compatibilityEntity(car);
        }
        @Override public CarriageInteractionCoordinatesPickingRayView toCompatibilityRay(AbstractContraptionEntity visible, AbstractContraptionEntity compatibility, Vec3 from, Vec3 to, float partial) {
            return CarriageInteractionCoordinates.toCompatibilityRay(visible, compatibility, from, to, partial);
        }
        @Override public Vec3 toWorld(AbstractContraptionEntity entity, Vec3 local) {
            return CarriageInteractionCoordinates.toWorld(entity, local);
        }
    };

    private final PartialModelInitOperations partialModelInit = new PartialModelInitOperations() {
        @Override public PartialModel dt206Frame() {
            return PartialModelInit.DT206_FRAME;
        }
        @Override public PartialModel sktb200Frame() {
            return PartialModelInit.SKTB200_FRAME;
        }
        @Override public PartialModel dt206Wheel() {
            return PartialModelInit.DT206_WHEEL;
        }
        @Override public PartialModel sktb200Wheel() {
            return PartialModelInit.SKTB200_WHEEL;
        }
        @Override public PartialModel crh1aMotorFrame() {
            return PartialModelInit.CRH1A_MOTOR_FRAME;
        }
        @Override public PartialModel crh1aMotorWheel() {
            return PartialModelInit.CRH1A_MOTOR_WHEEL;
        }
        @Override public PartialModel crh1aTrailerFrame() {
            return PartialModelInit.CRH1A_TRAILER_FRAME;
        }
        @Override public PartialModel crh1aTrailerWheel() {
            return PartialModelInit.CRH1A_TRAILER_WHEEL;
        }
    };

    private final SnapshotControlsClientOperations snapshotControlsClient = new SnapshotControlsClientOperations() {
        @Override public boolean click(SnapshotCarriageView carriage, BlockPos pos, Player player) {
            return SnapshotControlsClient.click(carriage, pos, player);
        }
        @Override public void startConfirmed(SnapshotCarriageView carriage, BlockPos pos) {
            SnapshotControlsClient.startConfirmed(carriage, pos);
        }
    };

    @Override public BlockEntityRenderBridge blockEntityRender() {
        return blockEntityRender;
    }

    @Override public BogeyRenderBridge bogeyRender() {
        return bogeyRender;
    }

    @Override public CabRenderBridge cabRender() {
        return cabRender;
    }

    @Override public CarriageRenderBridge carriageRender() {
        return carriageRender;
    }

    @Override public DisplayRenderBridge displayRender() {
        return displayRender;
    }

    @Override public DoorRenderBridge doorRender() {
        return doorRender;
    }

    @Override public TrackRenderBridge trackRender() {
        return trackRender;
    }

    @Override public CRH1HeadAnimationUpdatesOperations crh1HeadAnimationUpdates() {
        return crh1HeadAnimationUpdates;
    }

    @Override public CarriageControlPickingView createCarriageControlPicking(AbstractContraptionEntity carrier, Vec3 localFrom, Vec3 localTo, float partial) {
        return new CarriageControlPicking(carrier, localFrom, localTo, partial);
    }

    @Override public CarriageInteractionCoordinatesOperations carriageInteractionCoordinates() {
        return carriageInteractionCoordinates;
    }

    @Override public EntityRenderer<? extends SeatEntity> createHexieSeatRenderer(EntityRendererProvider.Context pContext) {
        return new HexieSeatRenderer(pContext);
    }

    @Override public PartialModelInitOperations partialModelInit() {
        return partialModelInit;
    }

    @Override public SS7DWiperVisualView createSS7DWiperVisual(VisualizationContext context, BlockEntityViews.SS7DHeadEntityView head, float partialTick) {
        return SS7DWiperVisual.create(context, head, partialTick);
    }

    @Override public SnapshotControlsClientOperations snapshotControlsClient() {
        return snapshotControlsClient;
    }
}
