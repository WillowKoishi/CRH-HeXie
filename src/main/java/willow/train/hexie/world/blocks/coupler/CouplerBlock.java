package willow.train.hexie.world.blocks.coupler;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CouplingBridge;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.world.collision.CouplerGeometry;

/** Coupling points are relative to the block centre with the model facing north. */
public interface CouplerBlock  extends CouplerBlockBridge.CouplerBlockView {
    /** Adapters allow optional mods to keep ownership of their blocks and block entities. */
    static CouplerBlockBridge.CouplerBlockView of(BlockState state) {
        var definition = HexieBridges.coupling().couplerDataRegistry().find(state);
        return definition != null ? definition : state.getBlock() instanceof CouplerBlock hook ? hook : null;
    }
    /** Identifies the mating head, independently of its visual model and motion parameters. */
    String model();
    /** Optional authored surfaces for head/cover interaction and body-attached cover animation. */
    default CouplerGeometry geometry() { return null; }
    /** Render-only state; stored block data remains unchanged. */
    default BlockState carriageRenderState(BlockState state, boolean shiftedBase) { return state; }
    default boolean assembled(BlockState state) { return false; }
    Direction facing(BlockState state);
    /** Vertical rotation axis in the block's north-facing X/Z plane. */
    CouplingBridge.CouplingEndpointAxisView axis();
    Point3 start();
    Point3 complete();
    default CouplingBridge.CouplingEndpointKind kind() { return CouplingBridge.CouplingEndpointKind.RIGID; }
    /** Additional arm travel from its resting length, in blocks; this does not limit swing. */
    default double maxExtension() { return CouplingBridge.CouplingEndpointOperations.DEFAULT_MAX_EXTENSION; }
    /** Some built-in couplers cannot mate until a persisted deployment animation completes. */
    default boolean requiresReadyState() { return false; }
    default boolean readyForCoupling(BlockState state, CompoundTag data, double time) { return true; }
    /** Opt-in for a coupling whose stored block state can require its existing connection to be released. */
    default boolean hasStateDrivenRelease() { return false; }
    default boolean releaseForState(BlockState state, CompoundTag data) { return false; }
    default boolean allowsAdapters() { return true; }
}
