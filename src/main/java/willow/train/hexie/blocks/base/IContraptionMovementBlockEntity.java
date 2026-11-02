package willow.train.hexie.blocks.base;

import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface IContraptionMovementBlockEntity {
    default void tickClientAnimation(long gameTime) {}
    public void doMovement(Contraption contraption, BlockPos blockPos, BlockState blockState, BlockEntity blockEntity);
}
