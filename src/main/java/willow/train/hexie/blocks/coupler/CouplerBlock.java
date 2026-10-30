package willow.train.hexie.blocks.coupler;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Coupling points are relative to the block centre with the model facing north. */
public interface CouplerBlock {
    /** Identifies the mating head, independently of its visual model and motion parameters. */
    default String model() { return "hexie:test"; }
    Direction facing(BlockState state);
    Point pivot();
    Point start();
    Point complete();
    default CouplingEndpoint.Kind kind() { return CouplingEndpoint.Kind.RIGID; }
    /** Additional arm travel from its resting length, in blocks; this does not limit swing. */
    default double maxExtension() { return CouplingEndpoint.DEFAULT_MAX_EXTENSION; }
}
