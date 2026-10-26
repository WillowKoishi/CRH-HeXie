package willow.train.hexie.blocks.bogeys.df11g;

import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlockEntity;
import willow.train.hexie.init.bogeys.BogeyStylesInit;

public class DF11GBogeyBlockEntity extends HexieBogeyBlockEntity {
    public DF11GBogeyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public BogeyStyle getDefaultStyle() {
        return BogeyStylesInit.DF11GBOGEY;
    }
}
