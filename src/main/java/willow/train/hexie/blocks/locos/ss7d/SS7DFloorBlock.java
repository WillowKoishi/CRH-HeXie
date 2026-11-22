package willow.train.hexie.blocks.locos.ss7d;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.blocks.carriage.TrainPanelPlacement;
import willow.train.hexie.blocks.parts.ModelRenderBounds;
import willow.train.hexie.item.FloorSwitcherItem;

/** Standalone one-block SS7D locomotive floor. */
public final class SS7DFloorBlock extends HorizontalDirectionalBlock implements IWrenchable, ModelRenderBounds {
    public static final MapCodec<SS7DFloorBlock> CODEC = simpleCodec(SS7DFloorBlock::new);
    /** 0 is the original plain floor; 1–4 are the four equipment orientations. */
    public static final IntegerProperty EQUIPMENT = IntegerProperty.create("equipment", 0, 4);
    private static final VoxelShape MODEL_SHAPE = Block.box(0, 12, 0, 16, 16, 16);
    // Match the walkable half-height collision used by the CRH1A/CRH2A floor blocks.
    private static final VoxelShape COLLISION_SHAPE = Block.box(0, 8, 0, 16, 16, 16);

    public SS7DFloorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(EQUIPMENT, 0));
    }

    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EQUIPMENT);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                      Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof FloorSwitcherItem && !player.isShiftKeyDown()) {
            if (player.isSpectator() || !player.mayBuild() || player.level() != level
                    || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack))
                return ItemInteractionResult.FAIL;
            if (!level.isClientSide && !level.setBlock(pos, state.cycle(EQUIPMENT), Block.UPDATE_ALL))
                return ItemInteractionResult.FAIL;
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        var placement = TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
        return placement != null ? placement : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** Snapshots written before the equipment option represent an empty underbody. */
    public static CompoundTag upgradeSnapshotProperties(CompoundTag properties) {
        if (properties.contains(EQUIPMENT.getName())) return properties;
        var upgraded = properties.copy();
        upgraded.putString(EQUIPMENT.getName(), "0");
        return upgraded;
    }

    @Override public AABB modelRenderBounds(BlockState state) {
        // The equipment projects beyond the floor cell in every possible orientation.
        return new AABB(-1, -0.125, -1, 2, 1, 2);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                         CollisionContext context) {
        return MODEL_SHAPE;
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                  CollisionContext context) {
        return COLLISION_SHAPE;
    }
}
