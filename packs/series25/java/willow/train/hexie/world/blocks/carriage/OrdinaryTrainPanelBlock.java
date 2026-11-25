package willow.train.hexie.world.blocks.carriage;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.world.blockinteract.CarriageBlockInteract;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.world.collision.CarriageCollisionShapes;

public class OrdinaryTrainPanelBlock extends SideMirrorBlockBase implements PassengerDisplayPanel, WorldBlockBridge.OrdinaryTrainPanelBlockView {

    private final boolean passengerDisplays;
    @Override public boolean supportsPassengerDisplay() { return passengerDisplays; }

Random random = new Random();

    public OrdinaryTrainPanelBlock(BlockBehaviour.Properties pProperties) {
        this(pProperties, true);
    }

    public OrdinaryTrainPanelBlock(BlockBehaviour.Properties pProperties, boolean passengerDisplays) {
        super(pProperties);
        this.passengerDisplays = passengerDisplays;
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return CarriageCollisionShapes.ForOrdinaryTrainPanelBlock.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return CarriageBlockInteract.ForOrdinaryTrainPanelBlock.use(pState, pLevel, pPos, pPlayer, pHand, pHit);
    }

    public void onBrush(
            Level pLevel,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit,
            BlockState blockState) {
        CarriageBlockInteract.ForOrdinaryTrainPanelBlock.onBrush(pLevel, pPlayer, pHand, pHit, blockState);
    }

    public void onLacqueredBoard(
            Level pLevel,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit,
            BlockState blockState) {
        CarriageBlockInteract.ForOrdinaryTrainPanelBlock.onLacqueredBoard(pLevel, pPlayer, pHand, pHit, blockState);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return CarriageBlockInteract.ForOrdinaryTrainPanelBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return CarriageBlockInteract.ForOrdinaryTrainPanelBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
