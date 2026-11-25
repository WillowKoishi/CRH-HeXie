package willow.train.hexie.bridge.compat;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import willow.train.hexie.bridge.world.CarriageBlockBridge;

public interface CreateDoorBridge {
    MovingInteractionBehaviour createCRH1DoorMemberInteraction();

    CRH1DoorMovementOperations crh1DoorMovement();

    interface CRH1DoorMovementOperations {
        CRH1DoorMovementView create();
        boolean fullyOpen(Contraption contraption, BlockPos lower, CarriageBlockBridge.CRH1DoorBlockView door);
    }

    CRH2DoorMovementOperations crh2DoorMovement();

    interface CRH2DoorMovementOperations {
        CRH2DoorMovementView create();
        boolean fullyOpen(Contraption contraption, BlockPos lower, CarriageBlockBridge.CRH2DoorBlockView door);
    }

    interface CRH1DoorMovementView extends MovementBehaviour {}

    interface CRH2DoorMovementView extends MovementBehaviour {}
}
