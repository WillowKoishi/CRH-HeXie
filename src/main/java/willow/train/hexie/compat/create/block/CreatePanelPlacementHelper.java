package willow.train.hexie.compat.create.block;

import com.simibubi.create.foundation.placement.PoleHelper;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2PanelBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2PartBlockView;
import willow.train.hexie.bridge.world.PartBlockBridge.PanelExtension;
import willow.train.hexie.bridge.world.PartBlockBridge.PanelPlacementBackend;
import willow.train.hexie.bridge.world.PartBlockBridge.PartStructureView;
import willow.train.hexie.core.util.general.InteractionResults;

public final class CreatePanelPlacementHelper implements IPlacementHelper {
    private static final int HELPER = PlacementHelpers.register(new CreatePanelPlacementHelper());

    private static final int FLOOR_HELPER = PlacementHelpers.register(new CreatePanelPlacementHelper(true));
    private static final int ROOF_HELPER = PlacementHelpers.register(new SideRunHelper("carport"));
    private static final int SKIRT_HELPER = PlacementHelpers.register(new SideRunHelper("skirt"));
    private final boolean floor;
    private CreatePanelPlacementHelper() { this(false); }
    private CreatePanelPlacementHelper(boolean floor) { this.floor = floor; }
    private static boolean isFloor(BlockState state) {
        return HexieBridges.partBlock().panelExtension(state)==PanelExtension.FLOOR;
    }
    private static boolean isSideRun(BlockState state,String kind) {
        var extension=HexieBridges.partBlock().panelExtension(state);
        return state.hasProperty(HorizontalDirectionalBlock.FACING)&&(kind.equals("carport")
                ? extension==PanelExtension.ROOF
                : extension==PanelExtension.SKIRT);
    }

    /** Keep Create's shaft end selection, but only consider the immediately adjacent cell. */
    private static final class SideRunHelper extends PoleHelper<Direction> {
        private final String kind;
        SideRunHelper(String kind) {
            super(state -> isSideRun(state, kind), state -> {
                Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
                return kind.equals("carport") && HexieBridges.partBlock().panelRunsAlongFacing(state)
                        ? facing.getAxis() : facing.getClockWise().getAxis();
            }, HorizontalDirectionalBlock.FACING);
            this.kind = kind;
        }
        @Override public int attachedPoles(Level level, BlockPos pos, Direction direction) {
            return 0;
        }
        @Override public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem item && isSideRun(((BlockItem) item.asItem()).getBlock().defaultBlockState(), kind);
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
            var initial = ((BlockItem) item.asItem()).getBlock().getStateForPlacement(new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
            if (initial == null) return PlacementOffset.fail();
            var placed = HexieBridges.partBlock().alignPanelForPlacement(initial, state);
            return PlacementOffset.success(offset.getBlockPos(), ignored -> placed).withGhostState(placed);
        }
    }

    public static PanelPlacementBackend register() {
        return CreatePanelPlacementHelper::place;
    }

    private static boolean isPanel(BlockState state) {
        return HexieBridges.partBlock().panelExtension(state)==PanelExtension.SIDE;
    }
    /** Null means this is not a panel-placement interaction; retain the block's other actions. */
    public static ItemInteractionResult place(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() || !player.mayBuild()) return null;
        var helper = PlacementHelpers.get(isFloor(state) ? FLOOR_HELPER : isSideRun(state, "carport") ? ROOF_HELPER : isSideRun(state, "skirt") ? SKIRT_HELPER : HELPER);
        if (!helper.matchesState(state) || !helper.matchesItem(stack)) return null;
        if (isSideRun(state, "carport") && !state.is(((BlockItem) stack.getItem()).getBlock())) return null;
        var offset = helper.getOffset(player, level, state, pos, hit, stack);
        if (stack.getItem() instanceof ToolOpsBridge.PartBlockItemView item) {
            if (!offset.isSuccessful()) return ItemInteractionResult.FAIL;
            var placed = offset.getTransform().apply(((BlockItem) item.asItem()).getBlock().defaultBlockState());
            var context = new BlockPlaceContext(level,player,hand,stack,hit);
            return InteractionResults.toItemResult(item.placeAtRoot(context,offset.getBlockPos(),placed));
        }
        if (!offset.isSuccessful()) return ItemInteractionResult.FAIL;
        offset.placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hit);
        return ItemInteractionResult.SUCCESS;
    }

    @Override public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item && (floor ? isFloor(((BlockItem) item.asItem()).getBlock().defaultBlockState()) : isPanel(((BlockItem) item.asItem()).getBlock().defaultBlockState()));
    }

    @Override public Predicate<BlockState> getStatePredicate() {
        return floor ? CreatePanelPlacementHelper::isFloor : CreatePanelPlacementHelper::isPanel;
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
            var placed = HexieBridges.partBlock().alignPanelForPlacement(initial, state);
            for (var direction : IPlacementHelper.orderedByDistanceExceptAxis(pos, hit.getLocation(), Direction.Axis.Y)) {
                var target = pos.relative(direction);
                if (level.getBlockState(target).canBeReplaced())
                    return PlacementOffset.success(target, ignored -> placed).withGhostState(placed);
            }
            return PlacementOffset.fail();
        }
        if (!(stack.getItem() instanceof BlockItem item) || !isPanel(state) || !matchesItem(stack)) return PlacementOffset.fail();
        var prototype = ((BlockItem) item.asItem()).getBlock().getStateForPlacement(
                new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
        if (prototype == null) return PlacementOffset.fail();
        var aligned = HexieBridges.partBlock().alignPanelForPlacement(prototype, state);
        if(state.getBlock() instanceof PartStructureView source) pos=source.partRoot(level,pos,state);
        var sourceBounds = state.getBlock() instanceof CRH2PartBlockView source ? source.placementBounds(state) : state.getShape(level,pos).bounds();
        var targetBounds = aligned.getBlock() instanceof CRH2PartBlockView targetBlock ? targetBlock.placementBounds(aligned) : aligned.getShape(level,pos).bounds();
        // A multiblock panel has one set of outer edges, not a separate selection area per cell.
        var localHit = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
        var normal = state.getValue(HorizontalDirectionalBlock.FACING).getAxis();
        var directions = Arrays.stream(Direction.values())
                .filter(direction -> direction.getAxis() != normal)
                .sorted(Comparator.comparingDouble(direction -> {
                    var axis = direction.getAxis();
                    double edge = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                            ? sourceBounds.max(axis) : sourceBounds.min(axis);
                    return Math.abs(localHit.get(axis) - edge);
                })).toList();
        for (var direction : directions) {
            var axis = direction.getAxis();
            double separation = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? sourceBounds.max(axis) - targetBounds.min(axis) : targetBounds.max(axis) - sourceBounds.min(axis);
            int step = Math.max(1, (int) Math.ceil(separation - ((state.getBlock() instanceof CRH2PanelBlockView || ((BlockItem) item.asItem()).getBlock() instanceof CRH2PanelBlockView) ? 1.0/64 : 1e-6)));
            var target = pos.relative(direction, step);
            if (!level.getBlockState(target).canBeReplaced()) continue;
            // Use the held item's selected panel type, instead of Copycat's default-state-only placement.
            var placementHit = new BlockHitResult(Vec3.atCenterOf(target), hit.getDirection(), target, false);
            var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, placementHit);
            var initial = ((BlockItem) item.asItem()).getBlock().getStateForPlacement(context);
            if (initial == null) continue;
            var placed = HexieBridges.partBlock().alignPanelForPlacement(initial, state);
            if (item instanceof ToolOpsBridge.PartBlockItemView crhItem && !crhItem.canFit(context,target,placed)) continue;
            return PlacementOffset.success(target, ignored -> placed).withGhostState(placed);
        }
        return PlacementOffset.fail();
    }
}
