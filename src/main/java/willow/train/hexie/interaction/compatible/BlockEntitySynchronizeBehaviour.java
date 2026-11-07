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
        else if (be instanceof willow.train.hexie.blockentity.CouplerBlockEntity coupler
                && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
            // Create's block-change packet updates the block map before rebuilding the display BEs;
            // the actor context can still contain the previous OPEN state on this client.
            var current = context.contraption.getBlocks().get(pos);
            coupler.setBlockState(current == null ? state : current.state());
            if (context.contraption.entity instanceof willow.train.hexie.compat.create.SnapshotCarriageEntity carriage) {
                for (var parameters : carriage.couplerAnimation().parameters()) {
                    if (!parameters.position().equals(pos)) continue;
                    var attachment = parameters.attachment();
                    var adapter = attachment == null ? null : attachment.adapter();
                    String baseModel = attachment == null ? ""
                            : attachment.reversed() ? adapter.secondModel() : adapter.firstModel();
                    coupler.updateRescueAnimationTarget(adapter, baseModel, parameters.coupled());
                    break;
                }
            }
            coupler.tickCoverAnimation();
        }
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
