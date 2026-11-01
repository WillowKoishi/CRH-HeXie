package willow.train.hexie.init;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.foundation.block.IBE;
import willow.train.hexie.interaction.compatible.BlockEntitySynchronizeBehaviour;
import willow.train.hexie.blocks.base.IContraptionMovementBlockEntity;


public class InteractionBehaviourInit {

    public static void register() {
        var timetable = new willow.train.hexie.compat.create.CreateConsoleTimetable();
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                block instanceof willow.train.hexie.blocks.locos.CRH2DrivingConsoleBlock ? timetable : null);
        BlockEntitySynchronizeBehaviour besb = new BlockEntitySynchronizeBehaviour();
        com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.registerProvider(
                block -> {
                    if(block instanceof IBE<?> ibe &&
                            IContraptionMovementBlockEntity.class.isAssignableFrom(ibe.getBlockEntityClass())) {
                        return besb;
                    }
                    return null;
                }
        );
    }

}
