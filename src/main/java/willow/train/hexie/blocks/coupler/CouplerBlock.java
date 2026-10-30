package willow.train.hexie.blocks.coupler;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;

/** Coupling points are relative to the block centre with the model facing north. */
public interface CouplerBlock {
    /** Adapters allow optional mods to keep ownership of their blocks and block entities. */
    static CouplerBlock of(BlockState state) {
        var definition = willow.train.hexie.vehicle.assembly.CouplerDataRegistry.find(state);
        return definition != null ? definition : state.getBlock() instanceof CouplerBlock hook ? hook : null;
    }
    /** Identifies the mating head, independently of its visual model and motion parameters. */
    String model();
    /** Render-only state; stored block data remains unchanged. */
    default BlockState carriageRenderState(BlockState state, boolean shiftedBase) { return state; }
    default boolean assembled(BlockState state) { return false; }
    Direction facing(BlockState state);
    Point pivot();
    Point start();
    Point complete();
    default CouplingEndpoint.Kind kind() { return CouplingEndpoint.Kind.RIGID; }
    /** Additional arm travel from its resting length, in blocks; this does not limit swing. */
    default double maxExtension() { return CouplingEndpoint.DEFAULT_MAX_EXTENSION; }
}
