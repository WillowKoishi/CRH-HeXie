package willow.train.hexie.interaction.item.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DriverSeatBlockView;

/** Places a console and its bundled chair together; both remain independent blocks. */
public final class DrivingConsoleItem extends PartBlockItem {
    private final Supplier<? extends CRH2DriverSeatBlockView> seat;

    public DrivingConsoleItem(Block block, Properties properties, Supplier<? extends CRH2DriverSeatBlockView> seat) {
        super(block, properties);
        this.seat = seat;
    }

    private Map<BlockPos, BlockState> placementCells(BlockPos root, BlockState state) {
        var facing = state.getValue(HexieBridges.worldBlock().drivingConsoleBlock().facing());
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        cells.put(root, state);
        cells.put(root.relative(facing.getOpposite()), seat.get().asBlock().defaultBlockState()
                .setValue(HexieBridges.carriageBlock().crh2DriverSeatBlock().facing(), facing));
        return cells;
    }

    private static boolean mayPlace(BlockPlaceContext context, BlockPos pos) {
        return context.getPlayer() == null
                || context.getPlayer().mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand());
    }

    @Override public boolean canFit(BlockPlaceContext context, BlockPos root, BlockState state) {
        return super.canFit(context, root, state)
                && HexieBridges.partBlock().partInstallation().available(context.getLevel(), placementCells(root, state),
                        pos -> false, pos -> mayPlace(context, pos));
    }

    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return HexieBridges.partBlock().partInstallation().install(context.getLevel(), placementCells(context.getClickedPos(), state),
                pos -> false, pos -> mayPlace(context, pos));
    }
}
