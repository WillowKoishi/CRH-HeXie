package willow.train.hexie.blocks.locos.ss7d;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Supplies the rain sensor with the windshield's world position when the cab is moving. */
public final class SS7DHeadMovement implements MovementBehaviour {
    @Override public Vec3 getActiveAreaOffset(MovementContext context) {
        return SS7DHeadGeometry.rotate(SS7DHeadEntity.RAIN_PROBE, context.state.getValue(SS7DHeadBlock.FACING))
                .subtract(.5, .5, .5);
    }
    @Override public boolean mustTickWhileDisabled() { return true; }
    @Override public void tick(MovementContext context) {
        if (!context.world.isClientSide || context.position == null) return;
        if (!(context.temporaryData instanceof SS7DWipers)) context.temporaryData = new SS7DWipers();
        var wipers = (SS7DWipers) context.temporaryData;
        wipers.tick(context.world, BlockPos.containing(context.position));
        if (context.contraption.getBlockEntityClientSide(context.localPos) instanceof SS7DHeadEntity head)
            head.useMovingWipers(wipers);
    }
}
