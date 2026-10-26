package willow.train.hexie.blocks.semaphore.through;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.semaphore.SemaphoreEntityInit;

public class AdjustableSignalBlock extends ThroughSignalBlock {
    public AdjustableSignalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(AdjustableSignalBlock::new);
    }

    @Override
    public BlockEntityType<? extends ThroughSignalBlockEntity> getBlockEntityType() {
        return SemaphoreEntityInit.ADJUSTABLE_SEMAPHORE.get();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof AdjustableSignalBlockEntity signal
                && context.getPlayer() != null && context.getPlayer().mayBuild()
                && !context.getLevel().isClientSide) {
            signal.orientation.rotateQuarterTurn();
        }
        return InteractionResult.SUCCESS;
    }
}
