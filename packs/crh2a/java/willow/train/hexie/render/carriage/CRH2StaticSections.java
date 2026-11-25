package willow.train.hexie.render.carriage;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2EndFaceBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2HeadBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2PartBlockView;
import willow.train.hexie.render.display.PassengerPanelModels;

/** CRH2's registration for the shared, continuously lit shell renderer. */
public final class CRH2StaticSections {
    private static final StaticShellSections SHELLS = new StaticShellSections(
            block -> block instanceof CRH2HeadBlockView || block instanceof CRH2PartBlockView || block instanceof CRH2EndFaceBlockView,
            state -> state.getBlock() instanceof CRH2HeadBlockView || state.getBlock() instanceof CRH2EndFaceBlockView
                    || state.getBlock() instanceof CRH2PartBlockView && state.getValue(HexieBridges.carriageBlock().crh2PartBlock().part()) == 0,
            state -> state.getBlock() instanceof CRH2PartBlockView,
            PassengerPanelModels::wrap, false);

    private CRH2StaticSections() {}
    public static void register() { SHELLS.register(); }
    public static boolean instanced(BlockPos pos) { return SHELLS.instanced(pos); }
    public static void changed(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        SHELLS.changed(world, pos, oldState, newState);
    }
    public static int lightAt(BlockAndTintGetter level, Map<BlockPos, Integer> cache, double x, double y, double z) {
        return StaticShellLighting.lightAt(level, cache, x, y, z);
    }
}
