package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlockEntity;

/** Each registered block has its own type, while sharing the existing HeXie data and lifecycle. */
public final class RegisteredBogeyBlockEntity extends HexieBogeyBlockEntity {
    RegisteredBogeyBlockEntity(BlockPos position, BlockState state) {
        super(((RegisteredBogeyBlock) state.getBlock()).getBlockEntityType(), position, state);
    }

    @Override public BogeyStyle getDefaultStyle() {
        return ((RegisteredBogeyBlock) getBlockState().getBlock()).getDefaultStyle();
    }
}
