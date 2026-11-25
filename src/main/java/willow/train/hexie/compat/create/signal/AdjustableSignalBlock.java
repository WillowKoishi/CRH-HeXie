package willow.train.hexie.compat.create.signal;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.trains.signal.SignalBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import willow.train.hexie.bridge.HexieBridges;

public class AdjustableSignalBlock extends ThroughSignalBlock {
    public AdjustableSignalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        // Create uses this property to restore the signal type when a track point is recreated.
        builder.add(SignalBlock.TYPE);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(AdjustableSignalBlock::new);
    }

    @Override
    @SuppressWarnings("unchecked")
    public BlockEntityType<? extends ThroughSignalBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends ThroughSignalBlockEntity>) HexieBridges.signalRegistry().semaphoreEntityInit().adjustableSemaphore();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return SignalBlockInteract.ForAdjustableSignalBlock.onWrenched(state, context);
    }
}
