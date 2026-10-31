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

public class OrdinaryTrainPanelBlock extends SideMirrorBlockBase {

    protected static final float AABB_OFFSET = 3.0F;
    protected static final VoxelShape SOUTH_AABB = Block.box(0, 0, 15, 16, 16, 17);
    protected static final VoxelShape EAST_AABB = Block.box(15, 0, 0, 17, 16, 16);
    protected static final VoxelShape NORTH_AABB = Block.box(0, 0, -1, 16, 16, 1);
    protected static final VoxelShape WEST_AABB = Block.box(-1, 0, 0, 1, 16, 16);

    Random random = new Random();

    public OrdinaryTrainPanelBlock(Properties pProperties) {
        super(pProperties);
    }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        switch ((Direction) p_54372_.getValue(FACING)) {
            case NORTH:
                return NORTH_AABB;
            case SOUTH:
                return SOUTH_AABB;
            case WEST:
                return WEST_AABB;
            case EAST:
            default:
                return EAST_AABB;
        }
    }

    public InteractionResult use(
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit) {
        if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.ColoredBrush.get())) {
            if (pLevel.isClientSide) {
                if (!PANEL_TYPE_UTIL.canTransformPanel(this)) return InteractionResult.PASS;
                int x = pPos.getX();
                int y = pPos.getY();
                int z = pPos.getZ();
                double speedFactor = 0.1;
                int particle_account = random.nextInt(12, 16);
                for (int i = 0; i < particle_account; i++) {
                    pLevel.addParticle(
                            ParticleTypes.HAPPY_VILLAGER,
                            (double) x + random.nextDouble(0f, 1.0f),
                            (double) y + random.nextDouble(0f, 1.0f),
                            (double) z + random.nextDouble(0f, 1.0f),
                            random.nextDouble(0f, speedFactor),
                            random.nextDouble(0f, speedFactor),
                            random.nextDouble(0f, speedFactor));
                }
                pLevel.playSound(
                        pPlayer, pPos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 8f, 1f);
                return InteractionResult.SUCCESS;
            }
            onBrush(pLevel, pPlayer, pHand, pHit, pState);

            return InteractionResult.SUCCESS;
        }
        if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.LACQUERED_BOARD.get())) {
            if (pLevel.isClientSide) {
                if (!PANEL_TYPE_UTIL.canTransformPanel(this)) return InteractionResult.PASS;
                int x = pPos.getX();
                int y = pPos.getY();
                int z = pPos.getZ();
                double speedFactor = 0.1;
                int particle_account = random.nextInt(12, 16);
                for (int i = 0; i < particle_account; i++) {
                    pLevel.addParticle(
                            ParticleTypes.HAPPY_VILLAGER,
                            (double) x + random.nextDouble(0f, 1.0f),
                            (double) y + random.nextDouble(0f, 1.0f),
                            (double) z + random.nextDouble(0f, 1.0f),
                            random.nextDouble(0f, speedFactor),
                            random.nextDouble(0f, speedFactor),
                            random.nextDouble(0f, speedFactor));
                }
                pLevel.playSound(
                        pPlayer, pPos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 8f, 1f);
                return InteractionResult.SUCCESS;
            }
            onLacqueredBoard(pLevel, pPlayer, pHand, pHit, pState);
            if (!pPlayer.isCreative()) {
                pPlayer.getItemInHand(pHand).shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    public void onBrush(
            Level pLevel,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit,
            BlockState blockState) {
        if (!PANEL_TYPE_UTIL.canTransformPanel(this)) return;
        pLevel.setBlock(
                pHit.getBlockPos(),
                BlockInit.TRAIN_BOTTOM_PANEL_BLOCK
                        .get()
                        .getStateForPlacement(
                                new BlockPlaceContext(
                                        pLevel, pPlayer, pHand, ItemStack.EMPTY, pHit),
                                PANEL_TYPE_UTIL.matchBottomBlock2Panel(this),
                                blockState.getValue(HINGE)),
                2);
        if(!pPlayer.isCreative()) {
            ItemStack stack = ColoredBrush.onEffectiveUse(pPlayer.getItemInHand(pHand));
            pPlayer.getInventory().setItem(pPlayer.getInventory().selected, stack);
        }
    }

    public void onLacqueredBoard(
            Level pLevel,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit,
            BlockState blockState) {
        if (!PANEL_TYPE_UTIL.canTransformPanel(this)) return;
        pLevel.setBlock(
                pHit.getBlockPos(),
                BlockInit.LAQUERED_BOARD_BLOCK
                        .get()
                        .getStateForPlacement(
                                new BlockPlaceContext(
                                        pLevel, pPlayer, pHand, ItemStack.EMPTY, pHit),
                                PANEL_TYPE_UTIL.matchBottomBlock2Panel(this),
                                blockState.getValue(HINGE)),
                2);
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
