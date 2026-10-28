package willow.train.hexie.blocks.signs;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import willow.train.hexie.blockentity.GroundStationSignEntity;
import java.util.List;

/** Nine occupied cells, with a single editable entity at bottom centre. */
public class GroundStationSignBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    public GroundStationSignBlock(Properties properties) {
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
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (int i = 0; i < 9; i++) {
            BlockPos p = cell(context.getClickedPos(), facing, i);
            if (context.getLevel().isOutsideBuildHeight(p) || !context.getLevel().getBlockState(p).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) for (int i = 0; i < 9; i++) if (i != 1)
            level.setBlock(cell(pos, state.getValue(FACING), i), state.setValue(PART, i), 3);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 1 ? new GroundStationSignEntity(pos, state) : null;
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return state.getValue(PART) == 1 ? RenderShape.MODEL : RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int part = state.getValue(PART), x = part % 3 - 1, y = part / 3;
        // Keep the space between the legs empty; clip each structural member per cell.
        VoxelShape result = Shapes.empty();
        double[][] members = {{-10, 0, -7, 40}, {23, 0, 26, 40}, {-7, 22, 23, 24}, {-8, 24, 24, 43}};
        for (double[] member : members) {
            double left = Math.max(0, member[0] - x * 16), right = Math.min(16, member[2] - x * 16);
            double bottom = Math.max(0, member[1] - y * 16), top = Math.min(16, member[3] - y * 16);
            if (left >= right || bottom >= top) continue;
            Direction facing = state.getValue(FACING);
            if (facing == Direction.SOUTH) { double old = left; left = 16 - right; right = 16 - old; }
            VoxelShape memberShape;
            if (facing == Direction.NORTH || facing == Direction.SOUTH) memberShape = box(left, bottom, 6, right, top, 10);
            else if (facing == Direction.WEST) memberShape = box(6, bottom, 16 - right, 10, top, 16 - left);
            else memberShape = box(6, bottom, left, 10, top, right);
            result = Shapes.or(result, memberShape);
        }
        return result;
    }
    private InteractionResult edit(BlockState state, Level level, BlockPos pos, Player player) {
        if (!player.mayBuild() || player.isSpectator()) return InteractionResult.PASS;
        BlockPos origin = root(pos, state);
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(origin) instanceof GroundStationSignEntity sign)
            server.openMenu(sign, buf -> { buf.writeBlockPos(origin); sign.writeFields(buf); });
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) { return edit(state, level, pos, player); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return willow.train.hexie.compat.InteractionResults.toItemResult(edit(state, level, pos, player));
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
}
