package willow.train.hexie.compat.create.contraption;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.WorldBlockBridge.MovingWipers;

/** Supplies the rain sensor with the windshield's world position when the cab is moving. */
public final class SS7DHeadMovement implements MovementBehaviour {
    @Override public Vec3 getActiveAreaOffset(MovementContext context) {
        return HexieBridges.worldBlock().ss7DHeadEntity().rainProbe(context.state)
                .subtract(.5, .5, .5);
    }
    @Override public boolean mustTickWhileDisabled() { return true; }
    @Override public void tick(MovementContext context) {
        if (!context.world.isClientSide || context.position == null) return;
        if (!(context.temporaryData instanceof MovingWipers))
            context.temporaryData = HexieBridges.worldBlock().ss7DHeadEntity().createMovingWipers();
        ((MovingWipers) context.temporaryData).tick(context.world, BlockPos.containing(context.position),
                context.contraption.getBlockEntityClientSide(context.localPos));
    }
}
