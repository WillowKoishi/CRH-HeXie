package willow.train.hexie.bridge.compat;

import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.StandardTrackPavingPresetOperations;
import willow.train.hexie.bridge.world.WorldBlockBridge.TrackBallastOperations;

public interface CreateTrackBridge extends TrackBallastOperations {
    TrackMaterial.TrackType hexieStandard();
    Block standardTrack();
    StandardTrackPavingPresetOperations pavingPreset();
    void sendTrackBallast(BlockPos origin, BlockPos target, int segment);
}
