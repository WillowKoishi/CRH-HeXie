package willow.train.hexie.blocks.ticketvendor;

import static willow.train.hexie.blocks.base.HorizontalBlockBase.FACING;

import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import willow.train.hexie.blockentity.TicketVendorEntity;


public class TicketVendorBlock extends BaseEntityBlock implements IWrenchable {
    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return simpleCodec(TicketVendorBlock::new);
    }

    public TicketVendorBlock(Properties settings) {
        super(settings);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> stateManager) {
        stateManager.add(FACING);
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter view, BlockPos pos, CollisionContext ctx) {
        return Block.box(0, 0, 0, 16, 32, 16);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    public InteractionResult use(
            BlockState state,
            Level world,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        return super.useWithoutItem(state, world, pos, player, hit);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TicketVendorEntity(pPos, pState);
    }

    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public net.minecraft.world.InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        return use(state, level, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    }

    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (stack.isEmpty()) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        net.minecraft.world.InteractionResult result = use(state, level, pos, player, hand, hit);
        return willow.train.hexie.compat.InteractionResults.toItemResult(result);
    }
}
