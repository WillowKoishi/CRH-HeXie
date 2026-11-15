package willow.train.hexie.vehicle.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** A structure member whose owner supplies an optional bogey for assembly. */
public interface OwnedBogeyMount {
    boolean isBogeyMount(BlockState state);
    BlockPos bogeyOwner(BlockPos position, BlockState state);
    BlockState bogeyState(BlockState ownerState);
    boolean bogeyEnabled(CompoundTag ownerData);
}
