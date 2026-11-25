package willow.train.hexie.world.blocks.carriage;

import java.util.List;
import java.util.Random;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blockinteract.CarriageBlockInteract;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.collision.CarriageCollisionShapes;
import willow.train.hexie.world.placement.CarriagePlacement;

public class Window25ZLevel extends HorizontalBlockBase {
    @Override public BlockState mirror(BlockState state,Mirror mirror) {
        return mirror==Mirror.NONE?state:super.mirror(state,mirror).cycle(HINGE);
    }

    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    public static final BooleanProperty MIRROR = BlockStateProperties.OPEN;
    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 1);

    Random random = new Random();

    public Window25ZLevel(BlockBehaviour.Properties p_49795_) {
        super(p_49795_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return CarriageCollisionShapes.ForWindow25ZLevel.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }

    // 猪
    public DoorHingeSide getHinge(BlockPlaceContext pContext) {
        return CarriagePlacement.ForWindow25ZLevel.getHinge(pContext);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return CarriagePlacement.ForWindow25ZLevel.getStateForPlacement(this, context, () -> super.getStateForPlacement(context));
    }

    public BlockState getStateForPlacement(
            BlockPlaceContext context, Direction direction, int type, DoorHingeSide hinge) {
        return CarriagePlacement.ForWindow25ZLevel.getStateForPlacement(context, direction, type, hinge, () -> super.getStateForPlacement(context));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(HINGE, TYPE, MIRROR);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return CarriageBlockInteract.ForWindow25ZLevel.use(this, pState, pLevel, pPos, pPlayer, pHand, pHit);
    }

    public boolean isMirrored(BlockState pState) {
        return pState.getValue(MIRROR);
    }

    @Override
    public List<ItemStack> getDrops(BlockState pState, LootParams.Builder pBuilder) {
        return List.of(this.asItem().getDefaultInstance());
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return CarriageBlockInteract.ForWindow25ZLevel.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return CarriageBlockInteract.ForWindow25ZLevel.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
