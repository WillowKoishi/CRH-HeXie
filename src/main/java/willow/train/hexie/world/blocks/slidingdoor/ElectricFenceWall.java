package willow.train.hexie.world.blocks.slidingdoor;

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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.core.api.interaction.HexieWrenchable;
import willow.train.hexie.world.blockinteract.FacilityBlockInteract;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.collision.FacilityCollision;
import willow.train.hexie.world.placement.FacilityPlacement;

public class ElectricFenceWall extends HorizontalBlockBase implements HexieWrenchable {

    public static final IntegerProperty TYPE = IntegerProperty.create("type", 0, 2);

    public ElectricFenceWall(BlockBehaviour.Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(
                this.defaultBlockState()
                        .setValue(TYPE, 0)
                        .setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(
            BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return FacilityCollision.ForElectricFenceWall.getShape(pState, pLevel, pPos, pContext);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return FacilityBlockInteract.ForElectricFenceWall.use(this, pState, pLevel, pPos, pPlayer, pHand, pHit);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_54370_) {
        super.createBlockStateDefinition(p_54370_);
        p_54370_.add(TYPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return FacilityPlacement.ForElectricFenceWall.getStateForPlacement(this, context);
    }

    public BlockState getStateForPlacement(Direction direction, int type) {
        return FacilityPlacement.ForElectricFenceWall.getStateForPlacement(this, direction, type);
    }

    public int nextType(BlockState state) {
        return FacilityBlockInteract.ForElectricFenceWall.nextType(state);
    }

    public int prevType(BlockState state) {
        return FacilityBlockInteract.ForElectricFenceWall.prevType(state);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return FacilityBlockInteract.ForElectricFenceWall.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return FacilityBlockInteract.ForElectricFenceWall.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
