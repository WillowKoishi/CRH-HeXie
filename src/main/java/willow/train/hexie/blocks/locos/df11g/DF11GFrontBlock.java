package willow.train.hexie.blocks.locos.df11g;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

public class DF11GFrontBlock extends HorizontalKineticBlock
//   implements ITE<DF11GFrontTileEntity>
{

    public DF11GFrontBlock(Properties properties) {
        super(properties);
    }

    //    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }


    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
        return List.of(this.asItem().getDefaultInstance());
    }
}
