package willow.train.hexie.world.blockinteract;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.core.util.general.InteractionResults;
import willow.train.hexie.world.blocks.base.HorizontalBlockBase;
import willow.train.hexie.world.blocks.carriage.CarPortBlockBGKZ;
import willow.train.hexie.world.blocks.carriage.CustomRenderedDoorBlock;
import willow.train.hexie.world.blocks.carriage.MultiType25ZMidBlock;
import willow.train.hexie.world.blocks.carriage.OBJRenderableEndFaceBlock;
import willow.train.hexie.world.blocks.carriage.OrdinaryTrainPanelBlock;
import willow.train.hexie.world.blocks.carriage.OrdinaryTrainUpperPanelBlock;
import willow.train.hexie.world.blocks.carriage.TopPanelSlabBlock;
import willow.train.hexie.world.blocks.carriage.Train25GLadderBlock;
import willow.train.hexie.world.blocks.carriage.Train25KTEndFaceBlock;
import willow.train.hexie.world.blocks.carriage.TrainIsolationDoorBlock;
import willow.train.hexie.world.blocks.carriage.TrainOpenableWindowBlock;
import willow.train.hexie.world.blocks.carriage.VehicleWindowBlock;
import willow.train.hexie.world.blocks.carriage.Window25ZLevel;
import willow.train.hexie.world.placement.PartInstallation;
import willow.train.hexie.world.placement.TrainPanelPlacement;
import willow.train.hexie.world.registry.blocks.BlockInit;
import willow.train.hexie.world.registry.items.EditablePanelItemInit;

public final class CarriageBlockInteract {
    private CarriageBlockInteract() {}

    public static final class ForTrain25BGEndFaceBlock {
        public static InteractionResult use(CarPortBlockBGKZ.Train25BGEndFaceBlock block, BlockState pState,
                    Level pLevel,
                    BlockPos pPos,
                    Player pPlayer,
                    InteractionHand pHand,
                    BlockHitResult pHit) {

                pState = pState.cycle(CarPortBlockBGKZ.Train25BGEndFaceBlock.OPEN);
                pLevel.setBlock(pPos, pState, 10);
                pLevel.gameEvent(
                        pPlayer,
                        block.isOpen(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE,
                        pPos);
                if (pState.getValue(CarPortBlockBGKZ.Train25BGEndFaceBlock.OPEN)) {
                    pLevel.playSound(
                            pPlayer, pPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 10, 10);
                } else {
                    pLevel.playSound(
                            pPlayer, pPos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 10, 10);
                }
                return InteractionResult.sidedSuccess(pLevel.isClientSide);
            }

        public static InteractionResult useWithoutItem(CarPortBlockBGKZ.Train25BGEndFaceBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(CarPortBlockBGKZ.Train25BGEndFaceBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForCustomRenderedDoorBlock {
        public static InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit, Supplier<InteractionResult> baseUseWithoutItem) {
            return baseUseWithoutItem.get();
        }

        public static InteractionResult useWithoutItem(CustomRenderedDoorBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(CustomRenderedDoorBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForMultiType25ZMidBlock {
        public static InteractionResult use(MultiType25ZMidBlock block, BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {
            if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.ColoredBrush.get())) {
                if (pLevel.isClientSide) {
                    int x = pPos.getX();
                    int y = pPos.getY();
                    int z = pPos.getZ();
                    double speedFactor = 0.1;
                    int particle_account = block.random.nextInt(12, 16);
                    for (int i = 0; i < particle_account; i++) {
                        pLevel.addParticle(
                                ParticleTypes.HAPPY_VILLAGER,
                                (double) x + block.random.nextDouble(0f, 1.0f),
                                (double) y + block.random.nextDouble(0f, 1.0f),
                                (double) z + block.random.nextDouble(0f, 1.0f),
                                block.random.nextDouble(0f, speedFactor),
                                block.random.nextDouble(0f, speedFactor),
                                block.random.nextDouble(0f, speedFactor));
                    }
                    pLevel.playSound(
                            pPlayer, pPos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 8f, 1f);
                    return InteractionResult.SUCCESS;
                }
                pState = pState.cycle(MultiType25ZMidBlock.TYPE);
                pLevel.setBlock(
                        pHit.getBlockPos(),
                        BlockInit.PANEL_25Z_ORIGINAL_MID
                                .get()
                                .getStateForPlacement(
                                        new BlockPlaceContext(
                                                pLevel, pPlayer, pHand, ItemStack.EMPTY, pHit),
                                        pState.getValue(HorizontalBlockBase.FACING),
                                        pState.getValue(MultiType25ZMidBlock.TYPE)),
                        2);
                return InteractionResult.sidedSuccess(pLevel.isClientSide);
            }
            return InteractionResult.FAIL;
        }

        public static InteractionResult useWithoutItem(MultiType25ZMidBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(MultiType25ZMidBlock block, ItemStack stack,
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

    public static final class ForOBJRenderableEndFaceBlock {
        public static InteractionResult use(OBJRenderableEndFaceBlock block, BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {

            pState = pState.cycle(OBJRenderableEndFaceBlock.OPEN);
            pLevel.setBlock(pPos, pState, 10);
            pLevel.gameEvent(
                    pPlayer, block.isOpen(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pPos);
            if (pState.getValue(OBJRenderableEndFaceBlock.OPEN)) {
                pLevel.playSound(pPlayer, pPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 10, 10);
            } else {
                pLevel.playSound(
                        pPlayer, pPos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 10, 10);
            }
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }

        public static InteractionResult useWithoutItem(OBJRenderableEndFaceBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(OBJRenderableEndFaceBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForOrdinaryTrainPanelBlock {
        public static InteractionResult use(BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {
            return PanelConversion.bottom(pLevel,pPos,pState,pPlayer,pHand,pHit);
        }

        public static void onBrush(Level pLevel,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit,
                BlockState blockState) {
            PanelConversion.bottom(pLevel,pHit.getBlockPos(),blockState,pPlayer,pHand,pHit);
        }

        public static void onLacqueredBoard(Level pLevel,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit,
                BlockState blockState) {
            PanelConversion.bottom(pLevel,pHit.getBlockPos(),blockState,pPlayer,pHand,pHit);
        }

        public static InteractionResult useWithoutItem(OrdinaryTrainPanelBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(OrdinaryTrainPanelBlock block, ItemStack stack,
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

    public static final class ForOrdinaryTrainUpperPanelBlock {
        public static InteractionResult use(BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {
            return PanelConversion.upper(pLevel,pPos,pState,pPlayer,pHand,pHit);
        }

        public static void onPaper(Level pLevel,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit,
                BlockState blockState) {
            PanelConversion.upper(pLevel,pHit.getBlockPos(),blockState,pPlayer,pHand,pHit);
        }

        public static InteractionResult useWithoutItem(OrdinaryTrainUpperPanelBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(OrdinaryTrainUpperPanelBlock block, ItemStack stack,
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

    public static final class ForTopPanelSlabBlock {
        public static InteractionResult use(BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {
            return PanelConversion.slab(pLevel,pPos,pState,pPlayer,pHand,pHit);
        }

        public static void onColoredBrush(Level pLevel,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit,
                BlockState pBlockState) {
            PanelConversion.slab(pLevel,pHit.getBlockPos(),pBlockState,pPlayer,pHand,pHit);
        }

        public static InteractionResult useWithoutItem(TopPanelSlabBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(TopPanelSlabBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            var placement = TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
            if (placement != null) return placement;
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForTrain25GLadderBlock {
        public static InteractionResult use(Train25GLadderBlock block, BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {

            pState = pState.cycle(Train25GLadderBlock.OPEN);
            pLevel.setBlock(pPos, pState, 10);
            pLevel.gameEvent(
                    pPlayer, block.isOpen(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pPos);
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }

        public static InteractionResult useWithoutItem(Train25GLadderBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(Train25GLadderBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForTrain25KTEndFaceBlock {
        public static InteractionResult use(Train25KTEndFaceBlock block, BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {

            pState = pState.cycle(Train25KTEndFaceBlock.OPEN);
            pLevel.setBlock(pPos, pState, 10);
            pLevel.gameEvent(
                    pPlayer, block.isOpen(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pPos);
            if (pState.getValue(Train25KTEndFaceBlock.OPEN)) {
                pLevel.playSound(pPlayer, pPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 10, 10);
            } else {
                pLevel.playSound(
                        pPlayer, pPos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 10, 10);
            }
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }

        public static InteractionResult useWithoutItem(Train25KTEndFaceBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(Train25KTEndFaceBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForTrainIsolationDoorBlock {
        public static InteractionResult use(TrainIsolationDoorBlock block, BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {

            pState = pState.cycle(TrainIsolationDoorBlock.OPEN);
            pLevel.setBlock(pPos, pState, 10);
            pLevel.gameEvent(
                    pPlayer, block.isOpen(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pPos);
            if (pState.getValue(TrainIsolationDoorBlock.OPEN)) {
                pLevel.playSound(pPlayer, pPos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 10, 10);
            } else {
                pLevel.playSound(
                        pPlayer, pPos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 10, 10);
            }
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }

        public static InteractionResult useWithoutItem(TrainIsolationDoorBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(TrainIsolationDoorBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForTrainOpenableWindowBlock {
        public static InteractionResult use(TrainOpenableWindowBlock block, BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {

            pState = pState.cycle(TrainOpenableWindowBlock.OPEN);
            pLevel.setBlock(pPos, pState, 10);
            pLevel.gameEvent(
                    pPlayer, block.isOpen(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pPos);
            if (pState.getValue(TrainOpenableWindowBlock.OPEN)) {
                pLevel.playSound(
                        pPlayer, pPos, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 10, 10);
            } else {
                pLevel.playSound(
                        pPlayer, pPos, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 10, 10);
            }
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }

        public static InteractionResult useWithoutItem(TrainOpenableWindowBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(TrainOpenableWindowBlock block, ItemStack stack,
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

    public static final class ForTrainUnOpenableWindowBlock {
        public static ItemInteractionResult useItemOn(ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            var placement = TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
            return placement != null ? placement : baseUseItemOn.get();
        }
    }

    public static final class ForVehicleWindowBlock {
        public static InteractionResult onWrenched(VehicleWindowBlock block, BlockState state,UseOnContext context) {
            var next=block.getRotatedBlockState(state,context.getClickedFace());var pos=context.getClickedPos();var level=context.getLevel();
            if(state.equals(next))return InteractionResult.PASS;
            if(!PartInstallation.replace(level,block.partCells(level,pos,state),block.partCells(level,pos,next),p->false,
                    p->context.getPlayer()==null||context.getPlayer().mayUseItemAt(p,context.getClickedFace(),context.getItemInHand())))return InteractionResult.FAIL;
            if(!level.isClientSide)BaseBlockInteract.playWrenchRotateSound(level,pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    public static final class ForWindow25ZLevel {
        public static InteractionResult use(Window25ZLevel block, BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {
            if (pPlayer.getItemInHand(pHand).is(EditablePanelItemInit.ColoredBrush.get())) {
                var target=BlockInit.ORIGINAL_COLOR_WINDOW_25Z_LEVEL.get().defaultBlockState()
                        .setValue(Window25ZLevel.TYPE,pState.getValue(Window25ZLevel.TYPE)==1?0:1);
                return PanelConversion.apply(pLevel,pPos,pState,target,pPlayer,pHand,pHit,true);
            }
            if (pPlayer.getMainHandItem().is(ItemStack.EMPTY.getItem())) {
                pState = pState.cycle(Window25ZLevel.MIRROR);
                pLevel.setBlock(pPos, pState, 10);
                pLevel.gameEvent(
                        pPlayer,
                        block.isMirrored(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE,
                        pPos);
                if (pState.getValue(Window25ZLevel.MIRROR)) {
                    pLevel.playSound(
                            pPlayer, pPos, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 10, 10);
                } else {
                    pLevel.playSound(
                            pPlayer, pPos, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 10, 10);
                }
                return InteractionResult.sidedSuccess(pLevel.isClientSide);
            }
            return InteractionResult.PASS;
        }

        public static InteractionResult useWithoutItem(Window25ZLevel block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(Window25ZLevel block, ItemStack stack,
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
}
