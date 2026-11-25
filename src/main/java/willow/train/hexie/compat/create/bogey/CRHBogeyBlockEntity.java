package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CRHBogeyBlockEntity extends HexieBogeyBlockEntity {
    public CRHBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public BogeyStyle getDefaultStyle() {
        return BogeyStylesInit.CRHBOGEY;
    }
}
