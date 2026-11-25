package willow.train.hexie.world.blockinteract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreatePanelBridge;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.interaction.ToolOpsBridge;
import willow.train.hexie.bridge.world.base.PantographBlockBase;
import willow.train.hexie.core.api.interaction.BlockControl;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.core.api.interaction.BlockControlUse;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.core.util.general.InteractionResults;
import willow.train.hexie.world.blocks.locos.CR200jFrontBlock;
import willow.train.hexie.world.blocks.locos.PantographBlock;
import willow.train.hexie.world.blocks.locos.SS7DCenterCarportBlock;
import willow.train.hexie.world.blocks.locos.SS7DDoorBlock;
import willow.train.hexie.world.blocks.locos.SS7DFloorBlock;
import willow.train.hexie.world.blocks.locos.SS7DHeadBlock;
import willow.train.hexie.world.blocks.locos.SS7DHeadEntity;
import willow.train.hexie.world.blocks.locos.SS7DHeadGeometry;
import willow.train.hexie.world.blocks.locos.SS7DSideCarportBlock;
import willow.train.hexie.world.placement.PartInstallation;
import willow.train.hexie.world.placement.TrainPanelPlacement;

public final class LocoBlockInteract {
    private LocoBlockInteract() {}

    public static final class ForCR200jFrontBlock {
        public static InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit, Supplier<InteractionResult> baseUseWithoutItem) {
            return baseUseWithoutItem.get();
        }

        public static InteractionResult useWithoutItem(CR200jFrontBlock block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(CR200jFrontBlock block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForPantographBlock {
        public static InteractionResult use(PantographBlockBase block, BlockState pState,
                Level pLevel,
                BlockPos pPos,
                Player pPlayer,
                InteractionHand pHand,
                BlockHitResult pHit) {

            pState = pState.cycle(PantographBlock.OPEN);
            pLevel.setBlock(pPos, pState, 10);
            pLevel.gameEvent(
                    pPlayer, block.isOpen(pState) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pPos);
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }

        public static InteractionResult useWithoutItem(PantographBlockBase block, BlockState state,
                Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            return block.use(state, level, pos, player, InteractionHand.MAIN_HAND, hit);
        }

        public static ItemInteractionResult useItemOn(PantographBlockBase block, ItemStack stack,
                BlockState state, Level level,
                BlockPos pos, Player player,
                InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            InteractionResult result = block.use(state, level, pos, player, hand, hit);
            return InteractionResults.toItemResult(result);
        }
    }

    public static final class ForSS7DCenterCarportBlock {
        public static ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            if (!(stack.getItem() instanceof ToolOpsBridge.FloorSwitcherItemView) || player.isShiftKeyDown())
                return baseUseItemOn.get();
            if (player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, hit.getDirection(), stack)) return ItemInteractionResult.FAIL;
            if (!level.isClientSide && !level.setBlock(pos, state.cycle(SS7DCenterCarportBlock.APPEARANCE), Block.UPDATE_ALL))
                return ItemInteractionResult.FAIL;
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        public static InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                Player player, BlockHitResult hit) {
            if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return InteractionResult.PASS;
            if (player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, pos)
                    || !player.mayUseItemAt(pos, hit.getDirection(), ItemStack.EMPTY)) return InteractionResult.FAIL;
            if (!level.isClientSide && !level.setBlock(pos, state.cycle(SS7DCenterCarportBlock.SIDE), Block.UPDATE_ALL))
                return InteractionResult.FAIL;
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    public static final class ForSS7DDoorBlock {
        public static ItemInteractionResult useItemOn(SS7DDoorBlock block, ItemStack stack, BlockState state, Level level,
                BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            if (!(stack.getItem() instanceof ToolOpsBridge.FloorSwitcherItemView) || player.isShiftKeyDown())
                return baseUseItemOn.get();
            if (player.isSpectator() || !player.mayBuild()) return ItemInteractionResult.FAIL;

            BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockPos upper = lower.above();
            BlockState bottom = level.getBlockState(lower);
            BlockState top = level.getBlockState(upper);
            if (!bottom.is(block) || !top.is(block) || bottom.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                    || top.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                    || !bottom.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).equals(top)) return ItemInteractionResult.FAIL;

            var previous = new LinkedHashMap<BlockPos, BlockState>();
            previous.put(lower, bottom);
            previous.put(upper, top);
            var next = new LinkedHashMap<BlockPos, BlockState>();
            next.put(lower, bottom.cycle(DoorBlock.HINGE));
            next.put(upper, top.cycle(DoorBlock.HINGE));
            boolean changed = PartInstallation.replace(level, previous, next, at -> false,
                    at -> level.mayInteract(player, at) && player.mayUseItemAt(at, hit.getDirection(), stack));
            return changed ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.FAIL;
        }
    }

    public static final class ForSS7DFloorBlock {
        public static ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                          Player player, InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            if (stack.getItem() instanceof ToolOpsBridge.FloorSwitcherItemView && !player.isShiftKeyDown()) {
                if (player.isSpectator() || !player.mayBuild() || player.level() != level
                        || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack))
                    return ItemInteractionResult.FAIL;
                if (!level.isClientSide && !level.setBlock(pos, state.cycle(SS7DFloorBlock.EQUIPMENT), Block.UPDATE_ALL))
                    return ItemInteractionResult.FAIL;
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            var placement = TrainPanelPlacement.place(stack, state, level, pos, player, hand, hit);
            return placement != null ? placement : baseUseItemOn.get();
        }
    }

    public static final class ForSS7DHeadBlock {
        private static final ControlSurface SURFACE = new ControlSurface(
                List.of(new ControlSurface.Part(SS7DHeadBlock.Control.NUMBER, null, ControlSurface.NO_BODY, null),
                        new ControlSurface.Part(SS7DHeadBlock.Control.COUPLER, null, ControlSurface.NO_BODY, null)),
                ControlSurface.NO_BODY, List.of());

        public static AABB controlBounds(SS7DHeadBlock block, BlockState state) { return block.modelRenderBounds(state); }

        public static ControlSurface surfaceGeometry(BlockState state) { return SURFACE; }

        public static BlockControlHit pickControl(SS7DHeadBlock block, BlockControlContext target, Vec3 from, Vec3 to) {
            var state = target.state();
            var pos = target.position();
            var a = CouplerInteraction.toModel(state, pos, from);
            var b = CouplerInteraction.toModel(state, pos, to);
            float open = CouplerInteraction.coverProgress(state);
            var hook = target.carrier() instanceof SnapshotCarriageView carriage
                    ? HexieBridges.createCarriage().snapshotCouplerModelInteraction().clip(carriage, pos, state, open, target.partialTick(), from, to)
                    : CouplerInteraction.fromModel(state, pos, block.geometry().clipMoving(open, a, b));
            BlockControlHit nearest = hook == null || !hook.head() ? null
                    : new BlockControlHit(SS7DHeadBlock.Control.COUPLER, new BlockHitResult(hook.point(), hook.face(), pos, false));
            boolean sidePlates = SS7DHeadBlock.hasSidePlates(target);
            for (var plate : SS7DHeadGeometry.PLATES) {
                var local = plate.intersect(a, b, plate.surface(sidePlates));
                if (local == null) continue;
                var hit = CouplerInteraction.fromModel(state, pos, local);
                if (nearest != null && nearest.blockHit().getLocation().distanceToSqr(from) <= hit.distanceToSqr(from))
                    continue;
                var face = plate.normal().add(.5, 0, .5);
                var normal = SS7DHeadGeometry.rotate(face, block.facing(state)).subtract(.5, 0, .5);
                nearest = new BlockControlHit(SS7DHeadBlock.Control.NUMBER, new BlockHitResult(hit,
                        Direction.getNearest((float) normal.x, 0, (float) normal.z), pos, false));
            }
            return nearest;
        }

        public static InteractionResult operateControl(SS7DHeadBlock block, BlockControlUse use, BlockControl control, int value) {
            if (!(use.player() instanceof ServerPlayer player) || player.isSpectator() || !player.mayBuild())
                return InteractionResult.PASS;
            var target = use.target();
            if (control == SS7DHeadBlock.Control.COUPLER && target.carrier() instanceof SnapshotCarriageView carriage)
                return HexieBridges.createCarriage().snapshotCouplerInteraction().handle(carriage, player, target.position(), use.hand())
                        ? InteractionResult.SUCCESS : InteractionResult.PASS;
            if (control != SS7DHeadBlock.Control.NUMBER || !(player.getItemInHand(use.hand()).getItem() instanceof ToolOpsBridge.ColoredBrushView))
                return InteractionResult.PASS;
            // Moving controls carry a requested point. Recheck its model surface before opening an editor.
            Vec3 eye = HexieBridges.interaction().controlInputTargets().toLocal(target, player, HexieBridges.interaction().controlInputTargets().eye(target, player));
            Vec3 point = use.hit().getLocation();
            var selected = block.pickControl(target, eye, point.add(point.subtract(eye).normalize().scale(.001)));
            if (selected == null || selected.control() != SS7DHeadBlock.Control.NUMBER
                    || selected.blockHit().getLocation().distanceToSqr(point) > .0001) return InteractionResult.FAIL;
            var panel = CreatePanelBridge.forCarrier(target.carrier());
            if (panel != null) {
                var captured = panel.createPanelEditTarget().createHeadNumber(player, target.carrier(), target.position(), target.position());
                return captured != null && HexieBridges.worldRegistry().ss7dNumberEditor().openMoving(player, captured)
                        ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            if (target.blocks().getBlockEntity(target.position()) instanceof SS7DHeadEntity head) {
                HexieBridges.worldRegistry().ss7dNumberEditor().openWorld(player, head);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        }
    }

    public static final class ForSS7DSideCarportBlock {
        public static ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                          Player player, InteractionHand hand, BlockHitResult hit, Supplier<ItemInteractionResult> baseUseItemOn) {
            if (!(stack.getItem() instanceof ToolOpsBridge.ColoredBrushView))
                return baseUseItemOn.get();
            if (!allowed(level, pos, player, hit, stack)) return ItemInteractionResult.FAIL;
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (!level.setBlock(pos, state.cycle(SS7DSideCarportBlock.ALTERNATE), Block.UPDATE_ALL)) return ItemInteractionResult.FAIL;
            if (!player.isCreative()) player.setItemInHand(hand, HexieBridges.toolOps().coloredBrush().onEffectiveUse(stack));
            return ItemInteractionResult.CONSUME;
        }

        public static InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                           Player player, BlockHitResult hit) {
            if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return InteractionResult.PASS;
            if (!allowed(level, pos, player, hit, ItemStack.EMPTY)) return InteractionResult.FAIL;
            if (!level.isClientSide && !level.setBlock(pos, state.cycle(SS7DSideCarportBlock.SIDE), Block.UPDATE_ALL))
                return InteractionResult.FAIL;
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        public static boolean allowed(Level level, BlockPos pos, Player player, BlockHitResult hit, ItemStack stack) {
            return player.level() == level && player.mayBuild() && !player.isSpectator()
                    && level.mayInteract(player, pos) && player.mayUseItemAt(pos, hit.getDirection(), stack);
        }
    }
}
