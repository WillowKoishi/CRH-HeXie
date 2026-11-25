package willow.train.hexie.compat.create.ui;

import com.simibubi.create.content.trains.track.ITrackBlock;
import net.minecraft.world.level.block.state.BlockState;

final class CreateTrackPredicate {
    private CreateTrackPredicate() {}

    static boolean isTrack(BlockState state) {
        return state.getBlock() instanceof ITrackBlock;
    }
}
