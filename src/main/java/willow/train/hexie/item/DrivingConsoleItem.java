package willow.train.hexie.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.DrivingConsoleBlock;
import willow.train.hexie.blocks.emu.CRH2DriverSeatBlock;
import willow.train.hexie.blocks.parts.PartInstallation;

/** Places a console and its bundled chair together; both remain independent blocks. */
public final class DrivingConsoleItem extends PartBlockItem {
    private final Supplier<? extends CRH2DriverSeatBlock> seat;

    public DrivingConsoleItem(Block block, Properties properties, Supplier<? extends CRH2DriverSeatBlock> seat) {
        super(block, properties);
        this.seat = seat;
    }

    private Map<BlockPos, BlockState> placementCells(BlockPos root, BlockState state) {
        var facing = state.getValue(DrivingConsoleBlock.FACING);
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        cells.put(root, state);
        cells.put(root.relative(facing.getOpposite()), seat.get().defaultBlockState()
                .setValue(CRH2DriverSeatBlock.FACING, facing));
        return cells;
    }

    private static boolean mayPlace(BlockPlaceContext context, BlockPos pos) {
        return context.getPlayer() == null
                || context.getPlayer().mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand());
    }

    @Override public boolean canFit(BlockPlaceContext context, BlockPos root, BlockState state) {
        return super.canFit(context, root, state)
                && PartInstallation.available(context.getLevel(), placementCells(root, state),
                        pos -> false, pos -> mayPlace(context, pos));
    }

    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return PartInstallation.install(context.getLevel(), placementCells(context.getClickedPos(), state),
                pos -> false, pos -> mayPlace(context, pos));
    }
}
