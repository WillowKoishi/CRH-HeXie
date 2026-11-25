package willow.train.hexie.world.blocks.emu;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.bridge.HexieBridges;

/** Decodes the virtual support in legacy assembled snapshots so those trains can still be disassembled. */
public final class CRH1HeadBogey {
    public static final String ENABLED = "HeadBogeyEnabled";
    private CRH1HeadBogey() {}
    public static BlockState state(Direction facing) {
        return HexieBridges.trackRegistry().trackBlockInit().crh1aTrailerBogey().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, facing.getAxis())
                .setValue(HeadBogey.headMountedProperty(), true);
    }
}
