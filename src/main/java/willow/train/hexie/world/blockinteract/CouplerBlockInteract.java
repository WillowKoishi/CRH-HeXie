package willow.train.hexie.world.blockinteract;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews;
import willow.train.hexie.world.blocks.coupler.AbstractCouplerBlock;

public final class CouplerBlockInteract {
    private CouplerBlockInteract() {}

    public static final class ForAbstractCouplerBlock {
        public static ItemInteractionResult useItemOn(AbstractCouplerBlock block, ItemStack stack, BlockState state,
                Level level, BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (block.geometry() == null || stack.is(Tags.Items.TOOLS_WRENCH))
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            var surface = CouplerInteraction.classifyHit(level, pos, state, hit);
            if (surface == null) return ItemInteractionResult.FAIL;
            if (surface.head()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!block.geometry().hasCovers()) return ItemInteractionResult.FAIL;
            if (player.isSpectator() || !player.mayBuild()) return ItemInteractionResult.FAIL;
            toggleCover(level, pos, state);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        public static boolean toggleCover(Level level, BlockPos pos, BlockState state) {
            var open = BlockStateProperties.OPEN;
            return state.hasProperty(open) && (level.isClientSide || level.setBlock(pos, state.cycle(open), Block.UPDATE_ALL));
        }

        public static InteractionResult useWithoutItem(AbstractCouplerBlock block, BlockState state,
                Level level, BlockPos pos, Player player,
                BlockHitResult hit) {
            if (block.geometry() != null) {
                if (player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH))
                    return InteractionResult.PASS;
                var surface = CouplerInteraction.classifyHit(level, pos, state, hit);
                if (surface == null) return InteractionResult.PASS;
                if (!surface.head()) {
                    if (!block.geometry().hasCovers()) return InteractionResult.PASS;
                    if (player.isSpectator() || !player.mayBuild()) return InteractionResult.FAIL;
                    toggleCover(level, pos, state);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
            if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || player.isSpectator() || !player.mayBuild()
                    || state.getValue(AbstractCouplerBlock.ASSEMBLED) || !(level.getBlockEntity(pos) instanceof BlockEntityViews.CouplerBlockEntityView entity))
                return InteractionResult.PASS;
            if (entity.uncouplePhysical())
                return InteractionResult.sidedSuccess(level.isClientSide);
            if (!level.isClientSide) {
                var removed = entity.removeOuter();
                if (removed != null) {
                    var item = HexieBridges.toolOps().couplerAdapterItem().stack(removed);
                    if (!player.getInventory().add(item)) player.drop(item, false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    public static final class ForJanneyCouplerBlock {
        public static ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (stack.is(Tags.Items.TOOLS_WRENCH))
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            var surface = CouplerInteraction.classifyHit(level, pos, state, hit);
            // Base and pins have no cover action. Let the existing adapter item handle head hits.
            return surface != null && surface.head() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    : ItemInteractionResult.FAIL;
        }
    }
}
