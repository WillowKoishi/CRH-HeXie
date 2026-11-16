package willow.train.hexie.blocks.emu.crh1a;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.init.track.TrackBlockInit;

/** Decodes the virtual support in legacy assembled snapshots so those trains can still be disassembled. */
public final class CRH1HeadBogey {
    public static final String ENABLED = "HeadBogeyEnabled";
    private CRH1HeadBogey() {}
    public static BlockState state(Direction facing) {
        return TrackBlockInit.CRH1A_TRAILER_BOGEY.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, facing.getAxis())
                .setValue(HexieBogeyBlock.HEAD_MOUNTED, true);
    }
}
