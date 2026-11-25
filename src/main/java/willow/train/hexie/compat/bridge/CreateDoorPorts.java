package willow.train.hexie.compat.bridge;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import willow.train.hexie.bridge.compat.CreateDoorBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.compat.create.interaction.CRH1DoorMemberInteraction;
import willow.train.hexie.compat.create.interaction.CRH1DoorMovement;
import willow.train.hexie.compat.create.interaction.CRH2DoorMovement;

final class CreateDoorPorts implements CreateDoorBridge {
    private final CRH1DoorMovementOperations crh1Movement = new CRH1DoorMovementOperations() {
        @Override public CRH1DoorMovementView create() {
            return new CRH1DoorMovement();
        }

        @Override public boolean fullyOpen(Contraption contraption, BlockPos lower, CarriageBlockBridge.CRH1DoorBlockView door) {
            return CRH1DoorMovement.fullyOpen(contraption, lower, door);
        }
    };

    private final CRH2DoorMovementOperations crh2Movement = new CRH2DoorMovementOperations() {
        @Override public CRH2DoorMovementView create() {
            return new CRH2DoorMovement();
        }

        @Override public boolean fullyOpen(Contraption contraption, BlockPos lower, CarriageBlockBridge.CRH2DoorBlockView door) {
            return CRH2DoorMovement.fullyOpen(contraption, lower, door);
        }
    };

    @Override public MovingInteractionBehaviour createCRH1DoorMemberInteraction() {
        return new CRH1DoorMemberInteraction();
    }

    @Override public CRH1DoorMovementOperations crh1DoorMovement() {
        return crh1Movement;
    }

    @Override public CRH2DoorMovementOperations crh2DoorMovement() {
        return crh2Movement;
    }
}
