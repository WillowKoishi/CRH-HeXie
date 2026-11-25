package willow.train.hexie.render.carriage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1EndFaceBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1HeadBlockView;

/** CRH1 owns its head and end-face registrations independently of the CRH2 block types. */
public final class CRH1StaticSections {
    private static final StaticShellSections SHELLS = new StaticShellSections(
            block -> block instanceof CRH1HeadBlockView || block instanceof CRH1EndFaceBlockView,
            state -> state.getBlock() instanceof CRH1HeadBlockView || state.getBlock() instanceof CRH1EndFaceBlockView,
            state -> false, (model, state) -> model, true);

    private CRH1StaticSections() {}
    public static void register() { SHELLS.register(); }
    public static boolean instanced(BlockPos pos) { return SHELLS.instanced(pos); }
    public static void changed(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        SHELLS.changed(world, pos, oldState, newState);
    }
}
