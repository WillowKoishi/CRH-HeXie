package willow.train.hexie.blocks.carriage;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

public class TrainDoorBlock extends DoorBlock implements IWrenchable {
    public TrainDoorBlock(Properties pProperties) {
        super(net.minecraft.world.level.block.state.properties.BlockSetType.OAK, pProperties);
    }

    @Override
    public boolean isPathfindable(
            BlockState pState, PathComputationType pType) {
        return true;
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        return true;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder pBuilder) {
        if(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
            return List.of();
        return super.getDrops(state, pBuilder);
    }
}
