package willow.train.hexie.world.blocks.carriage;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemView;
import willow.train.hexie.core.api.carriage.Carriage25FloorView;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blocks.parts.ModelRenderBounds;
import willow.train.hexie.world.placement.TrainPanelPlacement;

/** One floor cell, with independently selectable beams and visual underbody equipment. */
public final class Carriage25GFloorBlock extends HorizontalDirectionalBlock
        implements HexieWrenchable, ModelRenderBounds, Carriage25FloorView {
    public static final MapCodec<Carriage25GFloorBlock> CODEC = simpleCodec(Carriage25GFloorBlock::new);
    public static final BooleanProperty SIDE_BEAM_LEFT = BooleanProperty.create("side_beam_left");
    public static final BooleanProperty SIDE_BEAM_RIGHT = BooleanProperty.create("side_beam_right");
    /** Empty, vacuum toilet, brake module, battery, DC600V inverter. */
    public static final IntegerProperty EQUIPMENT = IntegerProperty.create("equipment", 0, 4);
    public static final IntegerProperty EQUIPMENT_ROTATION = IntegerProperty.create("equipment_rotation", 0, 3);
    private static final VoxelShape OUTLINE = Block.box(0, 11, 0, 16, 16, 16);
    private static final VoxelShape COLLISION = Block.box(0, 8, 0, 16, 16, 16);
    private static final AABB RENDER_BOUNDS = new AABB(-1.5, -0.125, -1.5, 2.5, 1, 2.5);

    public Carriage25GFloorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(SIDE_BEAM_LEFT, false).setValue(SIDE_BEAM_RIGHT, false)
                .setValue(EQUIPMENT, 0).setValue(EQUIPMENT_ROTATION, 0));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIDE_BEAM_LEFT, SIDE_BEAM_RIGHT, EQUIPMENT, EQUIPMENT_ROTATION);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        // Both beams now lie on the FACING axis, so reflection keeps their flags.
        // Equipment's baked north direction is clockwise from FACING; reflect
        // that extra quarter-turn as well as the selected equipment rotation.
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(EQUIPMENT_ROTATION, Math.floorMod(2 - state.getValue(EQUIPMENT_ROTATION), 4));
    }

    @Override public BlockState wrenchState(BlockState state, Direction face) {
        Direction facing = state.getValue(FACING);
        // The complete exported model has a fixed clockwise quarter-turn.
        if (face == facing) return state.cycle(SIDE_BEAM_LEFT);
        if (face == facing.getOpposite()) return state.cycle(SIDE_BEAM_RIGHT);
        if (face.getAxis() == Direction.Axis.Y) return state.cycle(EQUIPMENT);
        return null;
    }

    @Override public BlockState rotateEquipment(BlockState state) {
        return state.getValue(EQUIPMENT) == 0 ? state : state.cycle(EQUIPMENT_ROTATION);
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                     Player player, InteractionHand hand, BlockHitResult hit) {
        // Vanilla tries the empty main hand before an occupied offhand.
        ItemStack wrench = stack;
        if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND) wrench = player.getOffhandItem();
        if (wrench.getItem() instanceof FloorSwitcherItemView && !player.isShiftKeyDown()) {
            BlockState next = wrenchState(state, hit.getDirection());
            if (next != null) {
                if (!canEdit(player, level, pos, hit, wrench)) return ItemInteractionResult.FAIL;
                if (!level.isClientSide && !level.setBlock(pos, next, Block.UPDATE_ALL)) return ItemInteractionResult.FAIL;
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        var placement = TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
        return placement != null ? placement : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()
                || player.getOffhandItem().getItem() instanceof FloorSwitcherItemView) return InteractionResult.PASS;
        BlockState next = rotateEquipment(state);
        if (next == state) return InteractionResult.PASS;
        if (!canEdit(player, level, pos, hit, ItemStack.EMPTY)) return InteractionResult.FAIL;
        if (!level.isClientSide && !level.setBlock(pos, next, Block.UPDATE_ALL)) return InteractionResult.FAIL;
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean canEdit(Player player, Level level, BlockPos pos, BlockHitResult hit, ItemStack stack) {
        return !player.isSpectator() && player.mayBuild() && player.level() == level
                && level.mayInteract(player, pos) && player.mayUseItemAt(pos, hit.getDirection(), stack);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    @Override public AABB modelRenderBounds(BlockState state) { return RENDER_BOUNDS; }
}
