package willow.train.hexie.blocks.carriage;

import java.util.function.Predicate;
import willow.train.hexie.blocks.locos.crh2a.CRH2PanelBlock;
import willow.train.hexie.blocks.locos.crh2a.CRH2PartBlock;
import willow.train.hexie.item.CRH2PartItem;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blocks.locos.cr200j.CR200jPanelBlocks;
import willow.train.hexie.blocks.locos.hxd3d.HXD3DPanelBlocks;
import willow.train.hexie.blocks.locos.df11g.DF11GPanel3Wide;
import willow.train.hexie.blocks.structure.PanelBlock2;
import willow.train.hexie.blocks.structure.PanelBlock4;
import willow.train.hexie.blocks.structure.PanelBlockHalf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.blocks.signs.LacqueredBoardBlock;
import willow.train.hexie.blocks.signs.TrainNoBlock;
import willow.train.hexie.blocks.signs.TrainPanelBlock;

/** Copycat-panel placement: extend within the existing panel's plane. */
public final class TrainPanelPlacement implements IPlacementHelper {
    private static final int HELPER = PlacementHelpers.register(new TrainPanelPlacement());

    public static void register() {} // Initialize before the first placement/ghost preview.

    private static boolean isPanel(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CRH2PanelBlock) return true;
        boolean supported = block instanceof OrdinaryTrainPanelBlock || block instanceof OrdinaryTrainUpperPanelBlock
                || block instanceof TrainPanelBlock || block instanceof TrainNoBlock || block instanceof LacqueredBoardBlock
                || block instanceof PanelBlock25Side || block instanceof TrainGlassPanelBlock
                || block instanceof TrainOpenableWindowBlock || block instanceof TrainUnOpenableWindowBlock
                || block instanceof Window25ZLevel || block instanceof MultiType25ZMidBlock
                || block instanceof CR200jPanelBlocks || block instanceof HXD3DPanelBlocks || block instanceof DF11GPanel3Wide
                || block instanceof PanelBlock2 || block instanceof PanelBlock4 || block instanceof PanelBlockHalf;
        if (!supported || !state.hasProperty(SideMirrorBlockBase.FACING)) return false;
        var shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) return false;
        var axis = state.getValue(SideMirrorBlockBase.FACING).getAxis();
        // Some panel classes also contain roofs/noses. Only their thin vertical variants qualify.
        return shape.max(axis) - shape.min(axis) <= .5;
    }

    private static boolean reverseFacing(Block block) {
        return block instanceof TrainOpenableWindowBlock || block instanceof TrainUnOpenableWindowBlock;
    }

    public static BlockState align(BlockState placed, BlockState source) {
        Direction facing = source.getValue(SideMirrorBlockBase.FACING);
        if (reverseFacing(source.getBlock()) != reverseFacing(placed.getBlock())) facing = facing.getOpposite();
        placed = placed.setValue(SideMirrorBlockBase.FACING, facing);
        boolean hasMirror = source.hasProperty(SideMirrorBlockBase.HINGE) || reverseFacing(source.getBlock());
        boolean left = source.hasProperty(SideMirrorBlockBase.HINGE)
                ? source.getValue(SideMirrorBlockBase.HINGE) == DoorHingeSide.LEFT
                : reverseFacing(source.getBlock()) && source.getValue(BlockStateProperties.HALF) == Half.TOP;
        if (hasMirror && placed.hasProperty(SideMirrorBlockBase.HINGE))
            placed = placed.setValue(SideMirrorBlockBase.HINGE, left ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
        if (hasMirror && reverseFacing(placed.getBlock()))
            placed = placed.setValue(BlockStateProperties.HALF, left ? Half.TOP : Half.BOTTOM);
        if (!reverseFacing(source.getBlock()) && !reverseFacing(placed.getBlock())
                && source.hasProperty(BlockStateProperties.OPEN) && placed.hasProperty(BlockStateProperties.OPEN))
            placed = placed.setValue(BlockStateProperties.OPEN, source.getValue(BlockStateProperties.OPEN));
        return placed;
    }

    /** Null means this is not a panel-placement interaction; retain the block's other actions. */
    public static ItemInteractionResult place(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() || !player.mayBuild()) return null;
        var helper = PlacementHelpers.get(HELPER);
        if (!helper.matchesState(state) || !helper.matchesItem(stack)) return null;
        var offset = helper.getOffset(player, level, state, pos, hit, stack);
        if (stack.getItem() instanceof CRH2PartItem item) {
            if (!offset.isSuccessful()) return ItemInteractionResult.FAIL;
            var placed = offset.getTransform().apply(item.getBlock().defaultBlockState());
            var context = new BlockPlaceContext(level,player,hand,stack,hit);
            return willow.train.hexie.compat.InteractionResults.toItemResult(item.placeAtRoot(context,offset.getBlockPos(),placed.getValue(CRH2PartBlock.FACING)));
        }
        offset.placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hit);
        return ItemInteractionResult.SUCCESS;
    }

    @Override public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item && isPanel(item.getBlock().defaultBlockState());
    }

    @Override public Predicate<BlockState> getStatePredicate() {
        return TrainPanelPlacement::isPanel;
    }

    @Override public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
                                               BlockHitResult hit) {
        return getOffset(player, level, state, pos, hit, player.getMainHandItem());
    }

    @Override public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
                                               BlockHitResult hit, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !isPanel(state) || !matchesItem(stack)) return PlacementOffset.fail();
        var prototype = item.getBlock().getStateForPlacement(
                new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
        if (prototype == null) return PlacementOffset.fail();
        var aligned = align(prototype, state);
        var selectedCell=pos;
        if(state.getBlock() instanceof CRH2PartBlock source) pos=source.root(pos,state);
        var sourceBounds = state.getBlock() instanceof CRH2PartBlock source ? source.placementBounds(state) : state.getShape(level,pos).bounds();
        var targetBounds = aligned.getBlock() instanceof CRH2PartBlock targetBlock ? targetBlock.placementBounds(aligned) : aligned.getShape(level,pos).bounds();
        var directions = IPlacementHelper.orderedByDistanceExceptAxis(selectedCell, hit.getLocation(),
                state.getValue(SideMirrorBlockBase.FACING).getAxis());
        for (var direction : directions) {
            var axis = direction.getAxis();
            double separation = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? sourceBounds.max(axis) - targetBounds.min(axis) : targetBounds.max(axis) - sourceBounds.min(axis);
            int step = Math.max(1, (int) Math.ceil(separation - ((state.getBlock() instanceof CRH2PanelBlock || item.getBlock() instanceof CRH2PanelBlock) ? 1.0/64 : 1e-6)));
            var target = pos.relative(direction, step);
            if (!level.getBlockState(target).canBeReplaced()) continue;
            // Use the held item's selected panel type, instead of Copycat's default-state-only placement.
            var placementHit = new BlockHitResult(Vec3.atCenterOf(target), hit.getDirection(), target, false);
            var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, placementHit);
            var initial = item.getBlock().getStateForPlacement(context);
            if (initial == null) continue;
            var placed = align(initial, state);
            if (item instanceof CRH2PartItem crhItem && !crhItem.canFit(context,target,placed)) continue;
            return PlacementOffset.success(target, ignored -> placed).withGhostState(placed);
        }
        return PlacementOffset.fail();
    }
}
