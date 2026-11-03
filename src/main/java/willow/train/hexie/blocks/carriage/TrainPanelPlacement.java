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

    private static final int FLOOR_HELPER = PlacementHelpers.register(new TrainPanelPlacement(true));
    private static final int ROOF_HELPER = PlacementHelpers.register(new SideRunHelper("carport"));
    private static final int SKIRT_HELPER = PlacementHelpers.register(new SideRunHelper("skirt"));
    private final boolean floor;
    public TrainPanelPlacement() { this(false); }
    private TrainPanelPlacement(boolean floor) { this.floor = floor; }
    private static boolean isFloor(BlockState state) {
        return willow.train.hexie.blocks.parts.PartDefinitions.get(state).extension()==willow.train.hexie.blocks.parts.PartDefinitions.Extension.FLOOR;
    }
    private static boolean isSideRun(BlockState state,String kind) {
        var extension=willow.train.hexie.blocks.parts.PartDefinitions.get(state).extension();
        return state.hasProperty(SideMirrorBlockBase.FACING)&&(kind.equals("carport")
                ? extension==willow.train.hexie.blocks.parts.PartDefinitions.Extension.ROOF
                : extension==willow.train.hexie.blocks.parts.PartDefinitions.Extension.SKIRT);
    }

    /** Keep Create's shaft end selection, but only consider the immediately adjacent cell. */
    private static final class SideRunHelper extends com.simibubi.create.foundation.placement.PoleHelper<Direction> {
        private final String kind;
        SideRunHelper(String kind) {
            super(state -> isSideRun(state, kind), state -> state.getValue(SideMirrorBlockBase.FACING).getClockWise().getAxis(), SideMirrorBlockBase.FACING);
            this.kind = kind;
        }
        @Override public int attachedPoles(Level level, BlockPos pos, Direction direction) {
            return 0;
        }
        @Override public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem item && isSideRun(item.getBlock().defaultBlockState(), kind);
        }
        @Override public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult hit) {
            return getOffset(player, level, state, pos, hit, player.getMainHandItem());
        }
        @Override public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult hit, ItemStack stack) {
            if (!matchesState(state) || !matchesItem(stack)) return PlacementOffset.fail();
            if (kind.equals("carport") && !state.is(((BlockItem) stack.getItem()).getBlock()))
                return PlacementOffset.fail();
            var offset = super.getOffset(player, level, state, pos, hit);
            if (!offset.isSuccessful()) return offset;
            var item = (BlockItem) stack.getItem();
            var initial = item.getBlock().getStateForPlacement(new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
            if (initial == null) return PlacementOffset.fail();
            var placed = willow.train.hexie.blocks.parts.LegacyPartLayout.placed(align(initial, state));
            return PlacementOffset.success(offset.getBlockPos(), ignored -> placed).withGhostState(placed);
        }
    }

    public static void register() {} // Initialize before the first placement/ghost preview.

    private static boolean isPanel(BlockState state) {
        return willow.train.hexie.blocks.parts.PartDefinitions.get(state).extension()==willow.train.hexie.blocks.parts.PartDefinitions.Extension.SIDE;
    }
    private static boolean reverseFacing(Block block) {
        return willow.train.hexie.blocks.parts.PartDefinitions.get(block.defaultBlockState()).reverseFacing();
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
        var helper = PlacementHelpers.get(isFloor(state) ? FLOOR_HELPER : isSideRun(state, "carport") ? ROOF_HELPER : isSideRun(state, "skirt") ? SKIRT_HELPER : HELPER);
        if (!helper.matchesState(state) || !helper.matchesItem(stack)) return null;
        if (isSideRun(state, "carport") && !state.is(((BlockItem) stack.getItem()).getBlock())) return null;
        var offset = helper.getOffset(player, level, state, pos, hit, stack);
        if (stack.getItem() instanceof willow.train.hexie.item.PartBlockItem item) {
            if (!offset.isSuccessful()) return ItemInteractionResult.FAIL;
            var placed = offset.getTransform().apply(item.getBlock().defaultBlockState());
            var context = new BlockPlaceContext(level,player,hand,stack,hit);
            return willow.train.hexie.compat.InteractionResults.toItemResult(item.placeAtRoot(context,offset.getBlockPos(),placed));
        }
        if (!offset.isSuccessful()) return ItemInteractionResult.FAIL;
        offset.placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hit);
        return ItemInteractionResult.SUCCESS;
    }

    @Override public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item && (floor ? isFloor(item.getBlock().defaultBlockState()) : isPanel(item.getBlock().defaultBlockState()));
    }

    @Override public Predicate<BlockState> getStatePredicate() {
        return floor ? TrainPanelPlacement::isFloor : TrainPanelPlacement::isPanel;
    }

    @Override public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
                                               BlockHitResult hit) {
        return getOffset(player, level, state, pos, hit, player.getMainHandItem());
    }

    @Override public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
                                               BlockHitResult hit, ItemStack stack) {
        if (isFloor(state) && stack.getItem() instanceof BlockItem floorItem && isFloor(floorItem.getBlock().defaultBlockState())) {
            var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit);
            var initial = floorItem.getBlock().getStateForPlacement(context);
            if (initial == null) return PlacementOffset.fail();
            var placed = willow.train.hexie.blocks.parts.LegacyPartLayout.placed(align(initial, state));
            for (var direction : IPlacementHelper.orderedByDistanceExceptAxis(pos, hit.getLocation(), Direction.Axis.Y)) {
                var target = pos.relative(direction);
                if (level.getBlockState(target).canBeReplaced())
                    return PlacementOffset.success(target, ignored -> placed).withGhostState(placed);
            }
            return PlacementOffset.fail();
        }
        if (!(stack.getItem() instanceof BlockItem item) || !isPanel(state) || !matchesItem(stack)) return PlacementOffset.fail();
        var prototype = item.getBlock().getStateForPlacement(
                new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
        if (prototype == null) return PlacementOffset.fail();
        var aligned = willow.train.hexie.blocks.parts.LegacyPartLayout.placed(align(prototype, state));
        if(state.getBlock() instanceof willow.train.hexie.blocks.parts.PartStructure source) pos=source.partRoot(level,pos,state);
        var sourceBounds = state.getBlock() instanceof CRH2PartBlock source ? source.placementBounds(state) : state.getShape(level,pos).bounds();
        var targetBounds = aligned.getBlock() instanceof CRH2PartBlock targetBlock ? targetBlock.placementBounds(aligned) : aligned.getShape(level,pos).bounds();
        // A multiblock panel has one set of outer edges, not a separate selection area per cell.
        var localHit = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        var normal = state.getValue(SideMirrorBlockBase.FACING).getAxis();
        var directions = java.util.Arrays.stream(Direction.values())
                .filter(direction -> direction.getAxis() != normal)
                .sorted(java.util.Comparator.comparingDouble(direction -> {
                    var axis = direction.getAxis();
                    double edge = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                            ? sourceBounds.max(axis) : sourceBounds.min(axis);
                    return Math.abs(localHit.get(axis) - edge);
                })).toList();
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
            var placed = willow.train.hexie.blocks.parts.LegacyPartLayout.placed(align(initial, state));
            if (item instanceof willow.train.hexie.item.PartBlockItem crhItem && !crhItem.canFit(context,target,placed)) continue;
            return PlacementOffset.success(target, ignored -> placed).withGhostState(placed);
        }
        return PlacementOffset.fail();
    }
}
