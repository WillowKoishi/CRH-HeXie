package willow.train.hexie.api.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Position and rays use the containing world's storage coordinates, or a carrier's local coordinates. */
public record BlockControlContext(BlockGetter blocks, BlockPos position, Entity carrier, float partialTick) {
    public BlockControlContext { position = position.immutable(); }
    public BlockState state() { return blocks.getBlockState(position); }
    public double time() {
        return (carrier != null ? carrier.level().getGameTime()
                : blocks instanceof Level level ? level.getGameTime() : 0) + (double) partialTick;
    }
}
