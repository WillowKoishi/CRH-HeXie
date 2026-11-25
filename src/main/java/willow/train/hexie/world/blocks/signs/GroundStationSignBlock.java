package willow.train.hexie.world.blocks.signs;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.SignBlockBridge;
import willow.train.hexie.world.blockentity.sign.GroundStationSignEntity;
import willow.train.hexie.world.blockinteract.SignBlockInteract;
import willow.train.hexie.world.collision.SignCollision;
import willow.train.hexie.world.placement.SignPlacement;

/** Nine occupied cells, with a single editable entity at bottom centre. */
public class GroundStationSignBlock extends BaseEntityBlock implements SignBlockBridge.GroundStationSignBlockView {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    public GroundStationSignBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 1));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(GroundStationSignBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
    public static BlockPos cell(BlockPos root, Direction facing, int part) {
        return root.relative(facing.getClockWise(), part % 3 - 1).above(part / 3);
    }
    public static BlockPos root(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getClockWise(), 1 - state.getValue(PART) % 3).below(state.getValue(PART) / 3);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return SignPlacement.ForGroundStationSignBlock.getStateForPlacement(this, context);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        SignPlacement.ForGroundStationSignBlock.setPlacedBy(level, pos, state, placer, stack);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 1 ? new GroundStationSignEntity(pos, state) : null;
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return state.getValue(PART) == 1 ? RenderShape.MODEL : RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SignCollision.ForGroundStationSignBlock.getShape(state, level, pos, context);
    }
    public InteractionResult edit(BlockState state, Level level, BlockPos pos, Player player) {
        return SignBlockInteract.ForGroundStationSignBlock.edit(state, level, pos, player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return SignBlockInteract.ForGroundStationSignBlock.useWithoutItem(this, state, level, pos, player, hit);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return SignBlockInteract.ForGroundStationSignBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        super.onRemove(state, level, pos, replacement, moving);
        if (level.isClientSide || replacement.is(this)) return;
        BlockPos origin = root(pos, state);
        if (state.getValue(PART) != 1) {
            BlockState base = level.getBlockState(origin);
            if (base.is(this) && base.getValue(PART) == 1) level.destroyBlock(origin, true);
        } else for (int i = 0; i < 9; i++) if (i != 1) {
            BlockPos p = cell(origin, state.getValue(FACING), i);
            BlockState other = level.getBlockState(p);
            if (other.is(this) && root(p, other).equals(origin)) level.removeBlock(p, moving);
        }
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) != 1) level.destroyBlock(root(pos, state), false);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return state.getValue(PART) == 1 ? List.of(new ItemStack(this)) : List.of();
    }

    @Override public Block asBlock() { return this; }
}
