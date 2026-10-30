package willow.train.hexie.blocks.coupler;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.item.CouplerAdapterItem;
import willow.train.hexie.vehicle.assembly.CarriageFloorView;

/** Reusable coupler block behaviour; concrete blocks provide their model, geometry and coupling points. */
public abstract class AbstractCouplerBlock extends HorizontalDirectionalBlock implements CouplerBlock, EntityBlock {
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    protected AbstractCouplerBlock(Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ASSEMBLED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ASSEMBLED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        // The assembled flag changes only the static mesh; the complete coupler remains selectable.
        VoxelShape shape = couplerShape(state);
        return level instanceof CarriageFloorView carriage ? shape.move(0, -carriage.floorOffset(), 0) : shape;
    }

    @Override public Direction facing(BlockState state) { return state.getValue(FACING); }
    protected abstract VoxelShape couplerShape(BlockState state);

    @Override public BlockState carriageRenderState(BlockState state, boolean shiftedBase) {
        return shiftedBase ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : state.setValue(ASSEMBLED, true);
    }

    @Override public boolean assembled(BlockState state) { return state.getValue(ASSEMBLED); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CouplerBlockEntity(pos, state);
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        // Loot is evaluated on destruction; assembly removes the block without dropping its contents.
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CouplerBlockEntity coupler
                && coupler.adapter() != null) {
            for (var layer : coupler.adapter().items()) {
                ItemStack adapter = CouplerAdapterItem.stack(layer);
                if (!adapter.isEmpty()) drops.add(adapter);
            }
        }
        return drops;
    }

    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,
            net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || player.isSpectator() || !player.mayBuild()
                || state.getValue(ASSEMBLED) || !(level.getBlockEntity(pos) instanceof CouplerBlockEntity entity))
            return net.minecraft.world.InteractionResult.PASS;
        if (!level.isClientSide) {
            var removed = entity.removeOuter();
            if (removed != null) {
                var item = CouplerAdapterItem.stack(removed);
                if (!player.getInventory().add(item)) player.drop(item, false);
            }
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

}
