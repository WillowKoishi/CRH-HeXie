package willow.train.hexie.client.render;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.emu.crh2a.CRH2EndFaceBlock;
import willow.train.hexie.blocks.emu.crh2a.CRH2PartBlock;
import willow.train.hexie.blocks.emu.head.CRH2HeadBlock;
import willow.train.hexie.client.render.display.PassengerPanelModels;

/** CRH2's registration for the shared, continuously lit shell renderer. */
public final class CRH2StaticSections {
    private static final StaticShellSections SHELLS = new StaticShellSections(
            block -> block instanceof CRH2HeadBlock || block instanceof CRH2PartBlock || block instanceof CRH2EndFaceBlock,
            state -> state.getBlock() instanceof CRH2HeadBlock || state.getBlock() instanceof CRH2EndFaceBlock
                    || state.getBlock() instanceof CRH2PartBlock && state.getValue(CRH2PartBlock.PART) == 0,
            state -> state.getBlock() instanceof CRH2PartBlock,
            PassengerPanelModels::wrap, false);

    private CRH2StaticSections() {}
    public static void register() { SHELLS.register(); }
    public static boolean instanced(BlockPos pos) { return SHELLS.instanced(pos); }
    public static void changed(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        SHELLS.changed(world, pos, oldState, newState);
    }
    public static int lightAt(BlockAndTintGetter level, Map<BlockPos, Integer> cache, double x, double y, double z) {
        return StaticShellSections.lightAt(level, cache, x, y, z);
    }
}
