package willow.train.hexie.compat.create.contraption;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.world.BlockEntityViews.CouplerBlockEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.IContraptionMovementBlockEntityView;

public class BlockEntitySynchronizeBehaviour implements MovementBehaviour {

    @Override
    public void tick(MovementContext context) {
        var pos = context.localPos;
        var contraption = context.contraption;
        var entity = contraption.getBlockEntityClientSide(pos);
        if (entity instanceof IContraptionMovementBlockEntityView moving) {
            var info = contraption.getBlocks().get(pos);
            BlockState state = info != null ? info.state() : context.state;
            moving.doMovement(pos, state, entity);
        } else if (entity instanceof CouplerBlockEntityView coupler && context.state.hasProperty(BlockStateProperties.OPEN)) {
            var current = contraption.getBlocks().get(pos);
            tickCoupler(pos, current == null ? context.state : current.state(), coupler,
                    contraption.entity instanceof SnapshotCarriageView carriage ? carriage : null);
        }
    }

    private void tickCoupler(BlockPos pos, BlockState state, CouplerBlockEntityView coupler, SnapshotCarriageView carriage) {
        // The captured map receives OPEN updates before Create rebuilds the display block entities.
        coupler.asBlockEntity().setBlockState(state);
        if (carriage != null) {
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

    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    @Override
    public boolean disableBlockEntityRendering() {
        return false;
    }
}
