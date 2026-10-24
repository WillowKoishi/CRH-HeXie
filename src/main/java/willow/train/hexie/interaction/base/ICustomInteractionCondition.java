package willow.train.hexie.interaction.base;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface ICustomInteractionCondition {
    boolean test(BlockState state, @Nullable BlockEntity entity);
}
