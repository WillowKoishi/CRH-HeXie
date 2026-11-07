package willow.train.hexie.blocks.carriage;

import static willow.train.hexie.Main.PANEL_TYPE_UTIL;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.item.EditablePanelItemInit;
import willow.train.hexie.item.editable_panels.ColoredBrush;
import willow.train.hexie.blocks.base.SideMirrorBlockBase;

import java.util.Random;

public class OrdinaryTrainPanelBlock extends SideMirrorBlockBase implements willow.train.hexie.blocks.signs.PassengerDisplayPanel {

    private final boolean passengerDisplays;
    @Override public boolean supportsPassengerDisplay() { return passengerDisplays; }

protected static final VoxelShape SOUTH_AABB = Block.box(0, 0, 15, 16, 16, 17);
protected static final VoxelShape NORTH_AABB = Block.box(0, 0, -1, 16, 16, 1);
Random random = new Random();

    public OrdinaryTrainPanelBlock(Properties pProperties) {
        this(pProperties, true);
    }

    public OrdinaryTrainPanelBlock(Properties pProperties, boolean passengerDisplays) {
        super(pProperties);
        this.passengerDisplays = passengerDisplays;
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return willow.train.hexie.blocks.parts.PartShapeDefinitions.get("boundary_panel_2",p_54372_.getValue(FACING));
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        return PanelConversion.bottom(pLevel,pPos,pState,pPlayer,pHand,pHit);
    }

    public void onBrush(
            Level pLevel,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit,
            BlockState blockState) {
        PanelConversion.bottom(pLevel,pHit.getBlockPos(),blockState,pPlayer,pHand,pHit);
    }

    public void onLacqueredBoard(
            Level pLevel,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit,
            BlockState blockState) {
        PanelConversion.bottom(pLevel,pHit.getBlockPos(),blockState,pPlayer,pHand,pHit);
    }

    @Override
    public net.minecraft.world.InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        return use(state, level, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    }

    @Override
    public net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (stack.isEmpty()) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        var placement = willow.train.hexie.blocks.carriage.TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
        if (placement != null) return placement;
        net.minecraft.world.InteractionResult result = use(state, level, pos, player, hand, hit);
        return willow.train.hexie.compat.InteractionResults.toItemResult(result);
    }
}
