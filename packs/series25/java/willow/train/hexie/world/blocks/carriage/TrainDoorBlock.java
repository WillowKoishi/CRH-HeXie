package willow.train.hexie.world.blocks.carriage;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.placement.CarriagePlacement;

public class TrainDoorBlock extends DoorBlock implements HexieWrenchable {
    public TrainDoorBlock(BlockBehaviour.Properties pProperties) {
        super(BlockSetType.OAK, pProperties);
    }

    @Override
    public boolean isPathfindable(
            BlockState pState, PathComputationType pType) {
        return true;
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        return CarriagePlacement.ForTrainDoorBlock.canSurvive(pState, pLevel, pPos);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder pBuilder) {
        if(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
            return List.of();
        return super.getDrops(state, pBuilder);
    }
}
