package willow.train.hexie.world.blockinteract;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.core.util.general.InteractionResults;
import willow.train.hexie.world.blockentity.sign.CarriageNoSignEntity;
import willow.train.hexie.world.blockentity.sign.CarriageTypeSignEntity;
import willow.train.hexie.world.blockentity.sign.GroundStationSignEntity;
import willow.train.hexie.world.blockentity.sign.LacqueredBoardEntity;
import willow.train.hexie.world.blockentity.sign.TrainSpeedSignEntity;
import willow.train.hexie.world.blocks.signs.GroundStationSignBlock;
import willow.train.hexie.world.blocks.signs.LacqueredBoardBlock;
import willow.train.hexie.world.blocks.signs.TrainNoBlock;
import willow.train.hexie.world.blocks.signs.TrainPanelBlock;
import willow.train.hexie.world.blocks.signs.TrainSpeedSignBlock;
import willow.train.hexie.world.placement.TrainPanelPlacement;
import willow.train.hexie.world.registry.items.EditablePanelItemInit;

public final class SignBlockInteract {
    private SignBlockInteract() {}

    public static final class ForGroundStationSignBlock {
        public static InteractionResult edit(BlockState state, Level level, BlockPos pos, Player player) {
            if (!player.mayBuild() || player.isSpectator()) return InteractionResult.PASS;
            BlockPos origin = GroundStationSignBlock.root(pos, state);
            if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(origin) instanceof GroundStationSignEntity sign)
                server.openMenu(sign, buf -> { buf.writeBlockPos(origin); sign.writeFields(buf); });
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        public static InteractionResult useWithoutItem(GroundStationSignBlock block, BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) { return block.edit(state, level, pos, player); }

        public static ItemInteractionResult useItemOn(GroundStationSignBlock block, ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            return InteractionResults.toItemResult(block.edit(state, level, pos, player));
        }
    }

    public static final class ForLacqueredBoardBlock {
        public static InteractionResult use(BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {
            if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.ColoredBrush.get())) {
                if (!pLevel.isClientSide) {
                    ((ServerPlayer) pPlayer).openMenu( (LacqueredBoardEntity) pLevel.getBlockEntity(pPos), pPos);
                    return InteractionResult.PASS;
                }
                return InteractionResult.PASS;
            }
            return InteractionResult.PASS;
        }

        public static InteractionResult useWithoutItem(LacqueredBoardBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(LacqueredBoardBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            var placement = TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
            if (placement != null) return placement;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForPassengerDisplayPanel {
        public static boolean nativeInteraction() { return true; }

        public static ControlSurface surfaceGeometry(BlockState state) {
            return PanelAttachmentSurface.SURFACE;
        }

        public static AABB controlBounds(BlockState state) {
            return PanelAttachmentSurface.bounds(state);
        }

        public static BlockControlHit pickControl(BlockControlContext context,
                Vec3 from, Vec3 to) {
            return PanelAttachmentSurface.pick(context, from, to);
        }
    }

    public static final class ForTrainNoBlock {
        public static InteractionResult use(BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit, Supplier<InteractionResult> baseUseWithoutItem) {
            baseUseWithoutItem.get();
            CarriageNoSignEntity entity = ((CarriageNoSignEntity) pLevel.getBlockEntity(pPos));
            if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.ColoredBrush.get())) {
                if (pLevel.isClientSide) return InteractionResult.SUCCESS;
                ((ServerPlayer) pPlayer).openMenu( entity, pPos);
                // entity.markUpdated(false);
                return InteractionResult.SUCCESS;
            } else if (pPlayer.getItemInHand(pHand).is(ItemStack.EMPTY.getItem())) {
                entity.mirror();
                entity.setChanged();
                return InteractionResult.sidedSuccess(pLevel.isClientSide);
            }
            return InteractionResult.SUCCESS;
        }

        public static InteractionResult useWithoutItem(TrainNoBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(TrainNoBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            var placement = TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
            if (placement != null) return placement;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForTrainPanelBlock {
        public static InteractionResult use(TrainPanelBlock block, BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {
            if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.ColoredBrush.get())) {
                if (!pLevel.isClientSide) {
                    ((ServerPlayer) pPlayer).openMenu(
                            (CarriageTypeSignEntity) pLevel.getBlockEntity(pPos),
                            pPos);
                    // ((CarriageTypeSignEntity) pLevel.getBlockEntity(pPos)).markUpdated(false);
                    return InteractionResult.PASS;
                }
                return InteractionResult.PASS;
            }
            if (pPlayer.getMainHandItem().is(ItemStack.EMPTY.getItem())) {
                pState = pState.cycle(TrainPanelBlock.MIRROR);
                pLevel.setBlock(pPos, pState, 10);
                pLevel.gameEvent(
                        pPlayer,
                        block.isMirrored(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE,
                        pPos);
                pLevel.getChunk(pPos).getBlockEntity(pPos).setChanged();
                return InteractionResult.sidedSuccess(pLevel.isClientSide);
            }
            return InteractionResult.PASS;
        }

        public static InteractionResult useWithoutItem(TrainPanelBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(TrainPanelBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            var placement = TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
            if (placement != null) return placement;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForTrainSpeedSignBlock {
        public static InteractionResult use(BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {
            if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.ColoredBrush.get())) {
                if (pLevel.isClientSide) return InteractionResult.SUCCESS;
                TrainSpeedSignEntity entity = (TrainSpeedSignEntity) pLevel.getBlockEntity(pPos);
                ((ServerPlayer) pPlayer).openMenu( entity, pPos);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        }

        public static InteractionResult useWithoutItem(TrainSpeedSignBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(TrainSpeedSignBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }
}
