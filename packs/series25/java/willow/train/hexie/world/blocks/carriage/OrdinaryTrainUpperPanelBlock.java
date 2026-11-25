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
import org.jetbrains.annotations.NotNull;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.world.blockinteract.CarriageBlockInteract;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.world.collision.CarriageCollisionShapes;

public class OrdinaryTrainUpperPanelBlock extends SideMirrorBlockBase implements PassengerDisplayPanel {

    Random random = HexieConstants.HEXIE_RANDOM;

public OrdinaryTrainUpperPanelBlock(BlockBehaviour.Properties p_52325_) {
        super(p_52325_);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return CarriageCollisionShapes.ForOrdinaryTrainUpperPanelBlock.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return CarriageBlockInteract.ForOrdinaryTrainUpperPanelBlock.use(pState, pLevel, pPos, pPlayer, pHand, pHit);
    }

    public void onPaper(
            Level pLevel,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit,
            BlockState blockState) {
        CarriageBlockInteract.ForOrdinaryTrainUpperPanelBlock.onPaper(pLevel, pPlayer, pHand, pHit, blockState);
    }

    public @NotNull VoxelShape getVisualShape(
            BlockState p_48735_,
            BlockGetter p_48736_,
            BlockPos p_48737_,
            CollisionContext p_48738_) {
        return CarriageCollisionShapes.ForOrdinaryTrainUpperPanelBlock.getVisualShape(p_48735_, p_48736_, p_48737_, p_48738_);
    }

    public float getShadeBrightness(BlockState p_48731_, BlockGetter p_48732_, BlockPos p_48733_) {
        return 1.0F;
    }

    public boolean propagatesSkylightDown(
            BlockState p_48740_, BlockGetter p_48741_, BlockPos p_48742_) {
        return true;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state,
            Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return CarriageBlockInteract.ForOrdinaryTrainUpperPanelBlock.useWithoutItem(this, state, level, pos, player, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
            BlockState state, Level level,
            BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return CarriageBlockInteract.ForOrdinaryTrainUpperPanelBlock.useItemOn(this, stack, state, level, pos, player, hand, hit);
    }
}
