package willow.train.hexie.init;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.init.block.CustomRenderedDoorBlockInit;
import willow.train.hexie.interaction.HexieDoorMovingInteraction;
import willow.train.hexie.interaction.compatible.BlockEntitySynchronizeBehaviour;
import willow.train.hexie.interaction.compatible.RemoteControlInteractionBehaviour;
import willow.train.hexie.blocks.base.IContraptionMovementBlockEntity;

import java.util.function.Supplier;

public class InteractionBehaviourInit {

    public static void register() {
        // Create supports replacing tag-provided behaviors through its public registry.
        // This preserves its door handling while adding HeXie's coupler behavior.
        MovingInteractionBehaviour.REGISTRY.registerProvider(
                com.simibubi.create.api.registry.SimpleRegistry.Provider.forBlockTag(
                        BlockTags.WOODEN_DOORS, new HexieDoorMovingInteraction()));
        regForAllBlocks(
                RemoteControlInteractionBehaviour::new,
                CustomRenderedDoorBlockInit.CR200J_FRONT_BLOCK.get()
        );
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

    public static void regForAllBlocks(Supplier<? extends MovingInteractionBehaviour> supplier, Block... blocks) {
        for(Block b : blocks) {
            MovingInteractionBehaviour.REGISTRY.register(b, supplier.get());
        }
    }

}
