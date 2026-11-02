package willow.train.hexie.interaction.compatible;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.base.IContraptionMovementBlockEntity;

public class BlockEntitySynchronizeBehaviour implements MovementBehaviour {

    @Override
    public void tick(MovementContext context) {
        BlockPos pos = context.localPos;
        BlockEntity be = context.contraption.getBlockEntityClientSide(pos);
        BlockState state = context.state;
        if(be instanceof IContraptionMovementBlockEntity icmbe)
            icmbe.doMovement(context.contraption, pos, state, be);
    }

    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public boolean disableBlockEntityRendering() {
        return false;
    }
}
