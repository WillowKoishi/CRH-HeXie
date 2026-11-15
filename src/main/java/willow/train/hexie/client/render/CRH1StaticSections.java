package willow.train.hexie.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.blocks.emu.crh1a.CRH1HeadBlock;
import willow.train.hexie.blocks.emu.crh1a.CRH1EndFaceBlock;

/** CRH1 owns its head and end-face registrations independently of the CRH2 block types. */
public final class CRH1StaticSections {
    private static final StaticShellSections SHELLS = new StaticShellSections(
            block -> block instanceof CRH1HeadBlock || block instanceof CRH1EndFaceBlock,
            state -> state.getBlock() instanceof CRH1HeadBlock || state.getBlock() instanceof CRH1EndFaceBlock,
            state -> false, (model, state) -> model, true);

    private CRH1StaticSections() {}
    public static void register() { SHELLS.register(); }
    public static boolean instanced(BlockPos pos) { return SHELLS.instanced(pos); }
    public static void changed(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        SHELLS.changed(world, pos, oldState, newState);
    }
}
