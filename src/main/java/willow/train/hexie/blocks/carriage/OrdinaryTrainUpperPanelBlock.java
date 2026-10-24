package willow.train.hexie.blocks.carriage;

import static willow.train.hexie.Main.HEXIE_RANDOM;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.NotNull;

import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.item.EditablePanelItemInit;
import willow.train.hexie.blocks.base.SideMirrorBlockBase;

import java.util.Random;

public class OrdinaryTrainUpperPanelBlock extends SideMirrorBlockBase {

    Random random = HEXIE_RANDOM;
    protected static final float AABB_OFFSET = 3.0F;
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0D, 0.0D, 15, 16, 16, 16);
    protected static final VoxelShape WEST_AABB = Block.box(0, 0.0D, 0.0D, 1, 16.0D, 16.0D);
    protected static final VoxelShape NORTH_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1);
    protected static final VoxelShape EAST_AABB = Block.box(15, 0.0D, 0D, 16.0D, 16.0D, 16.0D);

    public OrdinaryTrainUpperPanelBlock(Properties p_52325_) {
        super(p_52325_);
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
        if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.Sticker.get())) {
            if (pLevel.isClientSide) {
                if (!PANEL_TYPE_UTIL.canTransformUpperPanel(this)) return InteractionResult.PASS;
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
            onPaper(pLevel, pPlayer, pHand, pHit, pState);
            if (!pPlayer.isCreative()) {
                pPlayer.getItemInHand(pHand).shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    public void onPaper(
            Level pLevel,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHit,
            BlockState blockState) {
        if (!PANEL_TYPE_UTIL.canTransformUpperPanel(this)) return;
        pLevel.setBlock(
                pHit.getBlockPos(),
                BlockInit.TRAIN_NO_BLOCK
                        .get()
                        .getStateForPlacement(
                                new BlockPlaceContext(
                                        pLevel, pPlayer, pHand, ItemStack.EMPTY, pHit),
                                blockState.getValue(HINGE),
                                PANEL_TYPE_UTIL.matchUpperBlock2Panel(this)),
                2);
    }

    public @NotNull VoxelShape getVisualShape(
            BlockState p_48735_,
            BlockGetter p_48736_,
            BlockPos p_48737_,
            CollisionContext p_48738_) {
        return Shapes.empty();
    }

    public float getShadeBrightness(BlockState p_48731_, BlockGetter p_48732_, BlockPos p_48733_) {
        return 1.0F;
    }

    public boolean propagatesSkylightDown(
            BlockState p_48740_, BlockGetter p_48741_, BlockPos p_48742_) {
        return true;
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
        net.minecraft.world.InteractionResult result = use(state, level, pos, player, hand, hit);
        return willow.train.hexie.compat.InteractionResults.toItemResult(result);
    }
}
