package willow.train.hexie.compat.bridge;

import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.bridge.compat.CreateTrackBridge;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.StandardTrackPavingPresetOperations;
import willow.train.hexie.compat.create.track.CreateTrackBlockInit;
import willow.train.hexie.compat.create.track.StandardTrackPavingPreset;
import willow.train.hexie.compat.create.track.TrackBallast;
import willow.train.hexie.compat.create.track.TrackBallastPacket;
import willow.train.hexie.compat.create.track.TrackMaterialInit;

final class CreateTrackPort implements CreateTrackBridge {
    @Override public Block standardTrack() { return CreateTrackBlockInit.standardTrack(); }
    @Override public TrackMaterial.TrackType hexieStandard() { return TrackMaterialInit.HEXIE_STANDARD; }
    @Override public StandardTrackPavingPresetOperations pavingPreset() { return StandardTrackPavingPreset.INSTANCE; }
    @Override public boolean enabled(BlockState state) { return TrackBallast.enabled(state); }
    @Override public boolean canPaint(Player player, ItemStack stack) { return TrackBallast.canPaint(player, stack); }

    @Override public void sendTrackBallast(BlockPos origin, BlockPos target, int segment) {
        PacketDistributor.sendToServer(new TrackBallastPacket(origin, target, segment));
    }
}
