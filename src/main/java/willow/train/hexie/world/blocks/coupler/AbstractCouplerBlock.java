package willow.train.hexie.world.blocks.coupler;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.InteractionBridge;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.bridge.world.CouplerBlockBridge;
import willow.train.hexie.world.blockentity.coupler.CouplerBlockEntity;
import willow.train.hexie.world.blockinteract.CouplerBlockInteract;
import willow.train.hexie.world.collision.CouplerCollision;
import willow.train.hexie.world.placement.CouplerPlacement;
import willow.train.hexie.world.registry.core.BlockEntitiesInit;

/** Reusable coupler block behaviour; concrete blocks provide their model, geometry and coupling points. */
public abstract class AbstractCouplerBlock extends HorizontalDirectionalBlock implements CouplerBlock, EntityBlock, InteractionBridge.NativeControlSurfaceView, CouplerBlockBridge.AbstractCouplerBlockView {
    public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");

    protected AbstractCouplerBlock(BlockBehaviour.Properties properties) {
        super(properties.dynamicShape());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ASSEMBLED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ASSEMBLED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return CouplerPlacement.ForAbstractCouplerBlock.getStateForPlacement(this, context);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return CouplerCollision.ForAbstractCouplerBlock.getShape(this, state, level, position, context);
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return CouplerCollision.ForAbstractCouplerBlock.getCollisionShape(this, state, level, position, context);
    }

    public VoxelShape placedShape(BlockState state, BlockGetter level) {
        return CouplerCollision.ForAbstractCouplerBlock.placedShape(this, state, level);
    }

    @Override public Direction facing(BlockState state) { return state.getValue(FACING); }
    public abstract VoxelShape couplerShape(BlockState state);

    @Override public BlockState carriageRenderState(BlockState state, boolean shiftedBase) {
        return shiftedBase ? Blocks.AIR.defaultBlockState()
                : state.setValue(ASSEMBLED, true);
    }

    @Override public boolean assembled(BlockState state) { return state.getValue(ASSEMBLED); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CouplerBlockEntity.create(pos, state);
    }

    private static boolean physicalCouplersLoaded() {
        return ModList.get().isLoaded("simurail") && ModList.get().isLoaded("sable");
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos,
            Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && physicalCouplersLoaded())
            HexieBridges.simurail().neighborChanged(level, pos);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BlockEntitiesInit.COUPLER.get()) return null;
        if (geometry() != null && state.hasProperty(BlockStateProperties.OPEN)
                || physicalCouplersLoaded() && state.is(HexieBridges.worldRegistry().blockInit().crh2aEndFace()))
            return (world, pos, blockState, entity) -> ((BlockEntityViews.CouplerBlockEntityView) entity).tickCoverAnimation();
        return null;
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return CouplerBlockInteract.ForAbstractCouplerBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }

    public static boolean toggleCover(Level level, BlockPos pos, BlockState state) {
        return CouplerBlockInteract.ForAbstractCouplerBlock.toggleCover(level, pos, state);
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        // Loot is evaluated on destruction; assembly removes the block without dropping its contents.
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BlockEntityViews.CouplerBlockEntityView coupler
                && coupler.adapter() != null) {
            for (var layer : coupler.adapter().items()) {
                ItemStack adapter = HexieBridges.toolOps().couplerAdapterItem().stack(layer);
                if (!adapter.isEmpty()) drops.add(adapter);
            }
        }
        return drops;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return CouplerBlockInteract.ForAbstractCouplerBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override public Block asBlock() { return this; }

    public final boolean hasCouplerCollision() { return hasCollision; }
}
