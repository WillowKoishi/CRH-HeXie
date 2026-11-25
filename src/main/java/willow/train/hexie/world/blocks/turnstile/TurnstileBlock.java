package willow.train.hexie.world.blocks.turnstile;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blockentity.misc.TurnstileBlockEntity;
import willow.train.hexie.world.blockinteract.FacilityBlockInteract;
import willow.train.hexie.world.collision.FacilityCollision;
import willow.train.hexie.world.placement.FacilityPlacement;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

public class TurnstileBlock extends BaseEntityBlock implements HexieWrenchable {

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(TurnstileBlock::new);
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public TurnstileBlock(BlockBehaviour.Properties arg) {
        super(arg);
        registerDefaultState(
                this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> stateManager) {
        stateManager.add(FACING).add(OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return FacilityPlacement.ForTurnstileBlock.getStateForPlacement(this, ctx);
    }

    public InteractionResult use(
            BlockState state,
            Level world,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return FacilityBlockInteract.ForTurnstileBlock.use(state, world, pos, player, hand, hit, () -> super.useWithoutItem(state, world, pos, player, hit));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, @NotNull UseOnContext context) {
        return FacilityBlockInteract.ForTurnstileBlock.onWrenched(state, context);
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return FacilityCollision.ForTurnstileBlock.getCollisionShape(this, state, world, pos, context);
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter view, BlockPos pos, CollisionContext ctx) {
        return FacilityCollision.ForTurnstileBlock.getShape(this, state, view, pos, ctx);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurnstileBlockEntity(BlockEntitiesInit.TURNS_A.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(
                type, BlockEntitiesInit.TURNS_A.get(), TurnstileBlockEntity::tick);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return FacilityBlockInteract.ForTurnstileBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return FacilityBlockInteract.ForTurnstileBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
