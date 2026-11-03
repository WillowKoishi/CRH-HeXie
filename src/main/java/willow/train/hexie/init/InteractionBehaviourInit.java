package willow.train.hexie.init;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.foundation.block.IBE;
import willow.train.hexie.interaction.compatible.BlockEntitySynchronizeBehaviour;
import willow.train.hexie.blocks.base.IContraptionMovementBlockEntity;


public class InteractionBehaviourInit {

    public static void register() {
        var doors=new com.simibubi.create.content.contraptions.behaviour.DoorMovingInteraction();
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                block instanceof willow.train.hexie.blocks.carriage.CustomRenderedDoorBlock ? doors : null);
        var timetable = new willow.train.hexie.compat.create.CreateConsoleTimetable();
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                willow.train.hexie.compat.create.CreateConsoleTimetable.supports(block) ? timetable : null);
        var variants = new willow.train.hexie.compat.create.CreatePartVariantInteraction();
        MovingInteractionBehaviour.REGISTRY.registerProvider(block ->
                block instanceof willow.train.hexie.blocks.parts.VariantPartBlock ? variants : null);
        BlockEntitySynchronizeBehaviour besb = new BlockEntitySynchronizeBehaviour();
        com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.registerProvider(
                block -> {
                    if (block instanceof willow.train.hexie.blocks.coupler.CouplerBlock hook && hook.geometry() != null) return besb;
                    if(block instanceof IBE<?> ibe &&
                            IContraptionMovementBlockEntity.class.isAssignableFrom(ibe.getBlockEntityClass())) {
                        return besb;
                    }
                    return null;
                }
        );
    }

}
