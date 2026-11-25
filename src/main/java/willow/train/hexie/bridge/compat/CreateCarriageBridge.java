package willow.train.hexie.bridge.compat;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.vehicle.VehicleRuntimeView;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.api.vehicle.Point3;

/** Create carriage assembly, movement, interaction and animation services. */
public interface CreateCarriageBridge {
    void invalidateClientStructures(Level level);

    CarriageChunkAccessOperations carriageChunkAccess();

    interface CarriageChunkAccessOperations {
        Level enter(Entity entity);
        void exit(Level previous);
    }

    ContraptionInteractionTargetsOperations contraptionInteractionTargets();

    interface ContraptionInteractionTargetsOperations {
        List<BlockPos> query(Contraption contraption, Vec3 localFrom, Vec3 localTo);
    }

    CreateCarriageNumberTargetOperations createCarriageNumberTarget();

    interface CreateCarriageNumberTargetOperations {
        void updateCapturedNumber(Contraption contraption, BlockPos pos, String number);
    }

    MovingInteractionBehaviour createEndFaceVariantInteraction();

    MovingInteractionBehaviour createFloorSkirtInteraction();

    MovingInteractionBehaviour createPartVariantInteraction();

    MovingInteractionBehaviour createPassengerDisplayInteraction();

    MovementBehaviour createPassengerDisplayMovement();

    PrefabSchematicOperations prefabSchematic();

    interface PrefabSchematicOperations {
        int MAX_COMPRESSED_BYTES = 524288;
        ItemStack create(ResourceLocation resource, byte[] compressed) throws IOException;
        void setIcon(ItemStack blueprint, ResourceLocation icon);
        ResourceLocation icon(ItemStack blueprint);
    }

    CarriageContraptionEntity createSnapshotCarriageEntity(EntityType<?> type, Level level);

    SnapshotCouplerInteractionOperations snapshotCouplerInteraction();

    interface SnapshotCouplerInteractionOperations {
        boolean handle(SnapshotCarriageView entity, Player player, BlockPos position, InteractionHand hand);
    }

    SnapshotCouplerModelInteractionOperations snapshotCouplerModelInteraction();

    interface SnapshotCouplerModelInteractionOperations {
        WorldBlockBridge.CouplerGeometryHitView clip(SnapshotCarriageView carriage, BlockPos position,
                BlockState state, float coverProgress, float partial, Vec3 from, Vec3 to);
    }

    SnapshotFloorInteractionOperations snapshotFloorInteraction();

    interface SnapshotFloorInteractionOperations {
        void apply(Player player, UUID carriage, BlockPos position, InteractionHand hand, int height);
        void applyLongitudinal(Player player, UUID carriage, BlockPos position, InteractionHand hand, int value);
    }

    interface AssembledVehicleMotionView {
        VehicleRuntimeView runtime();
        boolean updateCabKeyboard(Player player, SnapshotCarriageView car, BlockPos position, boolean toggleSimple,
                int mask);
    }

    interface SnapshotBogeyAnimationView {
        List<? extends SnapshotBogeyAnimationFrameView> frames();
        SnapshotBogeyAnimationFrameView frame(int index, float partialTick);
    }

    interface SnapshotBogeyAnimationFrameView {
        Quaternionf rotation();
        BlockPos position();
        float x();
        float y();
        float z();
        double wheelAngle();
        float speed();
        float qx();
        float qy();
        float qz();
        float qw();
    }

    interface SnapshotCouplerAnimationView {
        List<? extends SnapshotBogeyAnimationFrameView> frames();
        SnapshotBogeyAnimationFrameView frame(int index, float partialTick);
        SnapshotBogeyAnimationFrameView frameAt(BlockPos position, float partialTick);
        List<? extends SnapshotCouplerAnimationParametersView> parameters();
        double floorOffset();
        SnapshotCouplerAnimationParametersView parameters(int index, float partialTick);
    }

    interface SnapshotCouplerAnimationAdapterAttachmentView {
        CouplingBridge.CouplerAdapterView adapter();
        Point3 pivot();
        Point3 complete();
        boolean reversed();
    }

    interface SnapshotCouplerAnimationParametersView {
        UUID endpoint();
        BlockPos position();
        double extension();
        CouplingBridge.CouplerAnglesView angles();
        SnapshotCouplerAnimationAdapterAttachmentView attachment();
        boolean coupled();
        SnapshotCouplerAnimationPartnerPoseView partnerPose();
        CouplingBridge.CouplingEndpointVisualView visual();
    }

    interface SnapshotCouplerAnimationPartnerPoseView {
        Quaternionf rotation();
        Point3 transform(Point3 point);
        UUID endpoint();
        Point3 origin();
        CouplingBridge.CouplingEndpointRenderStateView state();
        float qx();
        float qy();
        float qz();
        float qw();
    }

    interface ContraptionWorldAccessorView {
        Contraption hexie$getContraption();
    }
}
