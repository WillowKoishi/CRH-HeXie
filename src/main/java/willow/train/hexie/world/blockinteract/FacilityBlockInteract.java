package willow.train.hexie.world.blockinteract;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.core.util.general.InteractionResults;
import willow.train.hexie.world.blockentity.misc.TurnstileBlockEntity;
import willow.train.hexie.world.blockentity.misc.TurnstileCRBlockEntity;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.misc.MegaphoneBlock;
import willow.train.hexie.world.blocks.slidingdoor.ElectricFenceWall;
import willow.train.hexie.world.blocks.ticketvendor.TicketVendorBlock;
import willow.train.hexie.world.blocks.turnstile.TurnstileBlock;
import willow.train.hexie.world.blocks.turnstile.TurnstileCRBlock;
import willow.train.hexie.world.placement.TrainPanelPlacement;
import willow.train.hexie.world.registry.blocks.BlockInit;
import willow.train.hexie.world.registry.core.SoundsInit;

public final class FacilityBlockInteract {
    private FacilityBlockInteract() {}

    public static final class ForDirectionalCarpetBlock {
        public static ItemInteractionResult useItemOn(ItemStack stack,
                BlockState state, Level level, BlockPos pos,
                Player player, InteractionHand hand,
                BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            var placement = TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
            return placement != null ? placement : baseUseItemOn.get();
        }
    }

    public static final class ForElectricFenceWall {
        private static final ResourceLocation WRENCH = ResourceLocation.fromNamespaceAndPath("create", "wrench");

        public static InteractionResult use(ElectricFenceWall block, BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {
            if (pPlayer.getItemInHand(pHand).getItemHolder().is(WRENCH)) {
                if (pLevel.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                int nextState = pPlayer.isShiftKeyDown() ? block.nextType(pState) : block.prevType(pState);
                pLevel.setBlock(
                        pPos,
                        BlockInit.ELECTRIC_FENCE_WALL_BLOCK
                                .get()
                                .getStateForPlacement(pState.getValue(HorizontalBlockBase.FACING), nextState),
                        2);
                BaseBlockInteract.playWrenchRotateSound(pLevel, pPos);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        public static int nextType(BlockState state) {
            int recent = state.getValue(ElectricFenceWall.TYPE);
            if (recent < 2) {
                return recent + 1;
            } else {
                return 0;
            }
        }

        public static int prevType(BlockState state) {
            int recent = state.getValue(ElectricFenceWall.TYPE);
            if (recent > 0) {
                return recent - 1;
            } else {
                return 2;
            }
        }

        public static InteractionResult useWithoutItem(ElectricFenceWall block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(ElectricFenceWall block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForMegaphoneBlock {
        public static @NotNull InteractionResult use(MegaphoneBlock block, @NotNull BlockState pState,
                Level pLevel,
                @NotNull BlockPos pPos,
                @NotNull Player pPlayer,
                @NotNull InteractionHand pHand,
                @NotNull BlockHitResult pHit) {
            if (pLevel.isClientSide) {

                return InteractionResult.SUCCESS;

            } else {
                pLevel.blockEvent(pPos, block, 0, 0);
                return InteractionResult.CONSUME;
            }
        }

        public static InteractionResult useWithoutItem(MegaphoneBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(MegaphoneBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForTicketVendorBlock {
        public static InteractionResult use(BlockState state,
                Level world,
                BlockPos pos,
                Player player,
                InteractionHand hand,
                BlockHitResult hit, Supplier<InteractionResult> baseUseWithoutItem) {
            if (world.isClientSide()) return InteractionResult.SUCCESS;
            return baseUseWithoutItem.get();
        }

        public static InteractionResult useWithoutItem(TicketVendorBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(TicketVendorBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForTurnstileBlock {
        public static InteractionResult use(BlockState state,
                Level world,
                BlockPos pos,
                Player player,
                InteractionHand hand,
                BlockHitResult hit, Supplier<InteractionResult> baseUseWithoutItem) {
            if (world.isClientSide()) return InteractionResult.SUCCESS;
            ItemStack stack = player.getItemInHand(hand);
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof TurnstileBlockEntity turnstile && TurnstilePayment.accept(stack, turnstile.exit)) {
                turnstile.setTimer(60);
                world.playSound(null, pos, SoundsInit.BEEP_TURNSTILE.get(), SoundSource.BLOCKS, 1f, 1f);
                return InteractionResult.SUCCESS;
            }
            return baseUseWithoutItem.get();
        }

        public static InteractionResult onWrenched(BlockState state, @NotNull UseOnContext context) {
            Level world = context.getLevel();
            Player player = context.getPlayer();
            if (player != null) {
                BlockEntity be = world.getBlockEntity(context.getClickedPos());
                if (be instanceof TurnstileBlockEntity turnstile) {
                    turnstile.exit = !turnstile.exit;
                    turnstile.setChanged();
                    player.displayClientMessage(
                            Component.translatable(
                                    "msg." + HexieConstants.MOD_ID + ".set_exit." + turnstile.exit),
                            true);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }

        public static InteractionResult useWithoutItem(TurnstileBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(TurnstileBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForTurnstileCRBlock {
        public static InteractionResult use(BlockState state,
                Level world,
                BlockPos pos,
                Player player,
                InteractionHand hand,
                BlockHitResult hit, Supplier<InteractionResult> baseUseWithoutItem) {
            if (world.isClientSide()) return InteractionResult.SUCCESS;
            ItemStack stack = player.getItemInHand(hand);
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof TurnstileCRBlockEntity turnstile && TurnstilePayment.accept(stack, turnstile.exit)) {
                turnstile.setTimer(60);
                world.playSound(null, pos, SoundsInit.BEEP_TURNSTILE.get(), SoundSource.BLOCKS, 1f, 1f);
                return InteractionResult.SUCCESS;
            }
            return baseUseWithoutItem.get();
        }

        public static InteractionResult onWrenched(BlockState state, @NotNull UseOnContext context) {
            Level world = context.getLevel();
            Player player = context.getPlayer();
            if (player != null) {
                BlockEntity be = world.getBlockEntity(context.getClickedPos());
                if (be instanceof TurnstileCRBlockEntity turnstile) {
                    turnstile.exit = !turnstile.exit;
                    turnstile.setChanged();
                    player.displayClientMessage(
                            Component.translatable(
                                    "msg." + HexieConstants.MOD_ID + ".set_exit." + turnstile.exit),
                            true);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }

        public static InteractionResult onSneakWrenched(TurnstileCRBlock block, BlockState state, UseOnContext context) {
            Level world = context.getLevel();
            Player player = context.getPlayer();
            if (player != null) {
                if (world.isClientSide) return InteractionResult.SUCCESS;
                world.setBlock(
                        context.getClickedPos(),
                        BlockInit.TURNS_B
                                .get()
                                .getStateForPlacement(state.getValue(TurnstileCRBlock.FACING), block.nextHinge(state)),
                        2);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        }

        public static int nextHinge(BlockState pState) {
            int hinge = pState.getValue(TurnstileCRBlock.HINGE);
            if (hinge < 2) {
                return hinge + 1;
            }
            return 0;
        }

        public static InteractionResult useWithoutItem(TurnstileCRBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(TurnstileCRBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }
}
