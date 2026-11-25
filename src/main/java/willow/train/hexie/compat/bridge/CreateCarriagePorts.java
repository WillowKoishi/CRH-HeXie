package willow.train.hexie.compat.bridge;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import willow.train.hexie.bridge.compat.CreateCarriageBridge;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.compat.create.assembly.PrefabSchematic;
import willow.train.hexie.compat.create.interaction.ContraptionInteractionTargets;
import willow.train.hexie.compat.create.interaction.CreateCarriageNumberTarget;
import willow.train.hexie.compat.create.interaction.CreateEndFaceVariantInteraction;
import willow.train.hexie.compat.create.interaction.CreateFloorSkirtInteraction;
import willow.train.hexie.compat.create.interaction.CreatePartVariantInteraction;
import willow.train.hexie.compat.create.interaction.CreatePassengerDisplayInteraction;
import willow.train.hexie.compat.create.interaction.CreatePassengerDisplayMovement;
import willow.train.hexie.compat.create.interaction.SnapshotCouplerInteraction;
import willow.train.hexie.compat.create.interaction.SnapshotCouplerModelInteraction;
import willow.train.hexie.compat.create.interaction.SnapshotFloorInteraction;
import willow.train.hexie.compat.create.motion.CarriageChunkAccess;
import willow.train.hexie.compat.create.motion.SnapshotCarriageEntity;

final class CreateCarriagePorts implements CreateCarriageBridge {
    private final CarriageChunkAccessOperations carriageChunkAccess = new CarriageChunkAccessOperations() {
        @Override public Level enter(Entity entity) {
            return CarriageChunkAccess.enter(entity);
        }

        @Override public void exit(Level previous) {
            CarriageChunkAccess.exit(previous);
        }
    };

    private final PrefabSchematicOperations prefabSchematic = new PrefabSchematicOperations() {
        @Override public ItemStack create(ResourceLocation resource, byte[] compressed) throws IOException {
            return PrefabSchematic.create(resource, compressed);
        }

        @Override public void setIcon(ItemStack blueprint, ResourceLocation icon) {
            PrefabSchematic.setIcon(blueprint, icon);
        }

        @Override public ResourceLocation icon(ItemStack blueprint) {
            return PrefabSchematic.icon(blueprint);
        }
    };

    private final SnapshotFloorInteractionOperations snapshotFloorInteraction = new SnapshotFloorInteractionOperations() {
        @Override public void apply(Player player, UUID carriage, BlockPos position, InteractionHand hand, int height) {
            SnapshotFloorInteraction.apply(player, carriage, position, hand, height);
        }

        @Override public void applyLongitudinal(Player player, UUID carriage, BlockPos position, InteractionHand hand, int value) {
            SnapshotFloorInteraction.applyLongitudinal(player, carriage, position, hand, value);
        }
    };

    @Override public void invalidateClientStructures(Level level) {
        for (var reference : ContraptionHandler.loadedContraptions
                .get(level).values()) {
            if (reference.get() instanceof SnapshotCarriageView carriage
                    && carriage.asEntity().getContraption() != null)
                carriage.asEntity().getContraption().invalidateClientContraptionStructure();
        }
    }

    @Override public CarriageChunkAccessOperations carriageChunkAccess() {
        return carriageChunkAccess;
    }

    @Override public ContraptionInteractionTargetsOperations contraptionInteractionTargets() {
        return ContraptionInteractionTargets::query;
    }

    @Override public CreateCarriageNumberTargetOperations createCarriageNumberTarget() {
        return CreateCarriageNumberTarget::updateCapturedNumber;
    }

    @Override public MovingInteractionBehaviour createEndFaceVariantInteraction() {
        return new CreateEndFaceVariantInteraction();
    }

    @Override public MovingInteractionBehaviour createFloorSkirtInteraction() {
        return new CreateFloorSkirtInteraction();
    }

    @Override public MovingInteractionBehaviour createPartVariantInteraction() {
        return new CreatePartVariantInteraction();
    }

    @Override public MovingInteractionBehaviour createPassengerDisplayInteraction() {
        return new CreatePassengerDisplayInteraction();
    }

    @Override public MovementBehaviour createPassengerDisplayMovement() {
        return new CreatePassengerDisplayMovement();
    }

    @Override public PrefabSchematicOperations prefabSchematic() {
        return prefabSchematic;
    }

    @Override public CarriageContraptionEntity createSnapshotCarriageEntity(EntityType<?> type, Level level) {
        return new SnapshotCarriageEntity(type, level);
    }

    @Override public SnapshotCouplerInteractionOperations snapshotCouplerInteraction() {
        return (entity, player, position, hand) -> SnapshotCouplerInteraction.handle((SnapshotCarriageEntity) entity, player, position, hand);
    }

    @Override public SnapshotCouplerModelInteractionOperations snapshotCouplerModelInteraction() {
        return (carriage, position, state, coverProgress, partial, from, to) -> SnapshotCouplerModelInteraction.clip((SnapshotCarriageEntity) carriage, position, state, coverProgress, partial, from, to);
    }

    @Override public SnapshotFloorInteractionOperations snapshotFloorInteraction() {
        return snapshotFloorInteraction;
    }
}
