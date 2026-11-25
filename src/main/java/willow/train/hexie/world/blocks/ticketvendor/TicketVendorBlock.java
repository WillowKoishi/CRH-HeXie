package willow.train.hexie.world.blocks.ticketvendor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blockentity.misc.TicketVendorEntity;
import willow.train.hexie.world.blockinteract.FacilityBlockInteract;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.collision.FacilityCollision;
import willow.train.hexie.world.placement.FacilityPlacement;

public class TicketVendorBlock extends BaseEntityBlock implements HexieWrenchable {
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(TicketVendorBlock::new);
    }

    public TicketVendorBlock(BlockBehaviour.Properties settings) {
        super(settings);
        registerDefaultState(this.stateDefinition.any().setValue(HorizontalBlockBase.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> stateManager) {
        stateManager.add(HorizontalBlockBase.FACING);
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter view, BlockPos pos, CollisionContext ctx) {
        return FacilityCollision.ForTicketVendorBlock.getShape(state, view, pos, ctx);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return FacilityPlacement.ForTicketVendorBlock.getStateForPlacement(this, ctx);
    }

    public InteractionResult use(
            BlockState state,
            Level world,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return FacilityBlockInteract.ForTicketVendorBlock.use(state, world, pos, player, hand, hit, () -> super.useWithoutItem(state, world, pos, player, hit));
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
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return FacilityBlockInteract.ForTicketVendorBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return FacilityBlockInteract.ForTicketVendorBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
