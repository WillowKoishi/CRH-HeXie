package willow.train.hexie.blocks.coupler;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Coupling points are relative to the block centre with the model facing north. */
public interface CouplerBlock {
    Direction facing(BlockState state);
    Point pivot();
    Point start();
    Point complete();
    default CouplingEndpoint.Kind kind() { return CouplingEndpoint.Kind.RIGID; }
}
