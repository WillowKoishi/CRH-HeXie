package willow.train.hexie.blocks.signs;

import static willow.train.hexie.Main.PANEL_TYPE_UTIL;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import willow.train.hexie.init.item.EditablePanelItemInit;
import willow.train.hexie.blockentity.TrainSpeedSignEntity;
import willow.train.hexie.blocks.base.SignBlockBase;
import willow.train.hexie.util.enums.SlabTypes;

import java.util.List;

public class TrainSpeedSignBlock extends SignBlockBase implements IWrenchable {

    protected static final VoxelShape AABB = Block.box(0, 8, 0, 16, 16, 16);

    public static final EnumProperty<SlabTypes> TYPE = EnumProperty.create("type", SlabTypes.class);

    public TrainSpeedSignBlock(Properties p_49795_) {
        super(p_49795_);
        this.getStateDefinition().any().setValue(TYPE, SlabTypes.S25B);
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return AABB;
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.ColoredBrush.get())) {
            if (pLevel.isClientSide) return InteractionResult.SUCCESS;
            TrainSpeedSignEntity entity = (TrainSpeedSignEntity) pLevel.getBlockEntity(pPos);
            ((ServerPlayer) pPlayer).openMenu( entity, pPos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TrainSpeedSignEntity(pPos, pState);
    }

    public BlockState getStateForPlacement(
            BlockPlaceContext context, Direction direction, SlabTypes types) {
        return super.getStateForPlacement(context)
                .setValue(TYPE, types)
                .setValue(FACING, direction);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(TYPE, SlabTypes.S25B);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_54370_) {
        super.createBlockStateDefinition(p_54370_);
        p_54370_.add(TYPE);
    }

    @Override
    public ItemStack getCloneItemStack(
            BlockState state, HitResult target, net.minecraft.world.level.LevelReader level, BlockPos pos, Player player) {
        return PANEL_TYPE_UTIL
                .matchSlabPanel2Block(state.getValue(TYPE))
                .asItem()
                .getDefaultInstance();
    }

    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
        return List.of(PANEL_TYPE_UTIL.matchSlabPanel2Block(pState.getValue(TYPE)).asItem().getDefaultInstance());
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
