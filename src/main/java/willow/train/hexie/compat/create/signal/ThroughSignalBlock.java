package willow.train.hexie.compat.create.signal;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import willow.train.hexie.bridge.HexieBridges;

/*
MIT License

Copyright (c) 2019 simibubi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

public class ThroughSignalBlock extends HorizontalDirectionalBlock implements IBE<ThroughSignalBlockEntity>, IWrenchable {

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(ThroughSignalBlock::new);
    }

    public static final EnumProperty<ThroughSignalBlock.SignalType> THROUGH_TYPE = EnumProperty.create("through_type", ThroughSignalBlock.SignalType.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public enum SignalType implements StringRepresentable {
        ENTRY_SIGNAL, CROSS_SIGNAL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public ThroughSignalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(THROUGH_TYPE, ThroughSignalBlock.SignalType.ENTRY_SIGNAL)
                .setValue(POWERED, false));
    }

    @Override
    public Class<ThroughSignalBlockEntity> getBlockEntityClass() {
        return ThroughSignalBlockEntity.class;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder.add(FACING).add(THROUGH_TYPE).add(POWERED));
    }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, SignalGetter world, BlockPos pos, Direction side) {
        return false;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return SignalPlacement.ForThroughSignalBlock.getStateForPlacement(this, pContext, () -> super.getStateForPlacement(pContext));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block changedBlock,
                                BlockPos changedPos, boolean moving) {
        if (!level.isClientSide) {
            updateFromRedstone(state, level, pos);
        }
    }

    private void updateFromRedstone(BlockState state, Level level, BlockPos pos) {
        boolean receivesPower = level.hasNeighborSignal(pos);
        if (receivesPower && !state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_CLIENTS);
        } else if (!receivesPower && state.getValue(POWERED)) {
            level.scheduleTick(pos, this, 4);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.hasNeighborSignal(pos) && state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, worldIn, pos, newState);
    }

    @Override
    @SuppressWarnings("unchecked")
    public BlockEntityType<? extends ThroughSignalBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends ThroughSignalBlockEntity>) HexieBridges.signalRegistry().semaphoreEntityInit().hxSemaphore();
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return SignalBlockInteract.ForThroughSignalBlock.onWrenched(this, state, context);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState pState) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState pState, Level blockAccess, BlockPos pPos) {
        return getBlockEntityOptional(blockAccess, pPos).filter(ThroughSignalBlockEntity::isPowered)
                .map($ -> 15)
                .orElse(0);
    }
}
