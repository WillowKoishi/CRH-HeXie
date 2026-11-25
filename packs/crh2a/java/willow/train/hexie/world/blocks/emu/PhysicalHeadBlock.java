package willow.train.hexie.world.blocks.emu;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.compat.SimurailBridge.HeadController;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.PhysicalHeadBlockView;
import willow.train.hexie.world.placement.PartInstallation;

/** One fishhead item, with the physical bogey's control block protruding one cell behind its floor. */
public final class PhysicalHeadBlock extends CRH2HeadBlock implements PhysicalHeadBlockView {
    private final Supplier<Block> controller;

    public PhysicalHeadBlock(Properties properties, Supplier<Block> controller) {
        super(properties);
        this.controller = controller;
    }

    @Override public boolean hasPhysicalBogey() { return true; }
    // A physical bogey exists before it reaches a rail; Simurail resolves contact in world space.
    @Override public boolean requiresTrackForBogey() { return false; }
    @Override public BlockPlaceContext placementContext(BlockPlaceContext context) {
        var face = context.getClickedFace();
        if (!face.getAxis().isHorizontal() || context.replacingClickedOnBlock()) return context;
        var rear = context.getClickedPos().relative(face.getOpposite());
        // Side placement anchors the complete rear edge to the clicked face. The extra controller
        // must sit outside that face, rather than attempt to replace the player's supporting block.
        if (!context.getLevel().getBlockState(rear).canBeReplaced()) {
            var root = context.getClickedPos().relative(face);
            var shifted = BlockPlaceContext.at(context, root, face);
            // The context factory can move again when root is occupied. Keep the intended anchor
            // and apply the replacement check that BlockItem performs before this hook.
            return shifted.getClickedPos().equals(root) && shifted.canPlace() ? shifted : null;
        }
        return context;
    }
    @Override public Map<BlockPos, BlockState> partCells(BlockGetter level, BlockPos root, BlockState state) {
        var cells = new LinkedHashMap<>(super.partCells(level, root, state));
        var facing = state.getValue(FACING);
        var control = root.relative(facing.getOpposite());
        var block = controller.get();
        var previous = level instanceof Level world && !world.hasChunkAt(control) ? block.defaultBlockState() : level.getBlockState(control);
        cells.put(control, (previous.is(block) ? previous : block.defaultBlockState()).setValue(FACING, facing));
        return cells;
    }
    @Override public void bogeyChanged(CRH2HeadEntityView head) {
        var level = head.asBlockEntity().getLevel();
        if (level == null || level.isClientSide || head.isCarriageView() || level.getBlockEntity(head.asBlockEntity().getBlockPos()) != head) return;
        var control = head.asBlockEntity().getBlockPos().relative(head.asBlockEntity().getBlockState().getValue(FACING).getOpposite());
        if (level.getBlockEntity(control) instanceof HeadController bogey) bogey.syncFromHead(head);
    }

    @Override public void onBlockStateChange(LevelReader world, BlockPos root, BlockState before, BlockState after) {
        if (!(world instanceof Level level) || level.isClientSide || PartInstallation.mutating()
                || !before.is(this) || !after.is(this) || before.getValue(FACING) == after.getValue(FACING)) {
            super.onBlockStateChange(world, root, before, after);
            return;
        }
        var oldControl = root.relative(before.getValue(FACING).getOpposite());
        var oldBogey = level.getBlockEntity(oldControl) instanceof HeadController bogey ? bogey : null;
        CompoundTag settings = oldBogey == null ? new CompoundTag() : oldBogey.safeSettings();
        CompoundTag saved = oldBogey == null ? null : oldBogey.asBlockEntity().saveWithFullMetadata(level.registryAccess());
        var newControl = root.relative(after.getValue(FACING).getOpposite());
        super.onBlockStateChange(world, root, before, after);
        var oldCells = partCells(level, root, before);
        var newCells = partCells(level, root, after);
        boolean available = PartInstallation.available(level, newCells,
                pos -> level.getBlockState(pos).equals(oldCells.get(pos)), pos -> true);
        if (available && oldBogey != null) oldBogey.beforeMove(newControl);
        boolean installed = available && PartInstallation.replace(level, oldCells, newCells, pos -> false, pos -> true, () -> {
            if (saved == null || !(level.getBlockEntity(newControl) instanceof HeadController moved)) return;
            var data = saved.copy();
            data.putInt("x", newControl.getX()); data.putInt("y", newControl.getY()); data.putInt("z", newControl.getZ());
            // The old auxiliary body is invalidated with its controller; a rotated controller creates fresh joints.
            data.remove("pivot"); data.remove("pivot_offset"); data.remove("pivot_rot");
            moved.asBlockEntity().loadWithComponents(data, level.registryAccess());
            moved.afterMove();
        });
        if (!installed) {
            PartInstallation.install(level, Map.of(root, before), root::equals, pos -> true);
            if (oldBogey != null) oldBogey.beforeMove(oldControl);
        }
        if (level.getBlockEntity(root) instanceof CRH2HeadEntityView head) {
            if (!settings.isEmpty()) head.updatePhysicalBogeyData(settings);
            // A failed preflight restores the old root and retains its original control entity.
            if (installed) bogeyChanged(head);
        }
    }
}
