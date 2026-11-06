package willow.train.hexie.client;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.network.HexieNetworkHandler;
import willow.train.hexie.network.c2s.ReleaseNativeControlsPacket;

public final class SnapshotControlsClient {
    private SnapshotControlsClient() {}

    public static boolean click(SnapshotCarriageEntity carriage, BlockPos pos, Player player) {
        // A held use key must not turn one click into repeated stop/start requests.
        Minecraft.getInstance().options.keyUse.setDown(false);
        var activePos = ControlsHandler.getControlsPos();
        if (ControlsHandler.getContraption() == carriage && activePos != null) {
            release(carriage, activePos);
            // Create sends its generic interaction (a toggle) only when this returns true.
            return false;
        }
        // Forward the normal interaction; wait for the server to grant control before capturing keys.
        return carriage.startControlling(pos, player);
    }

    public static void startConfirmed(SnapshotCarriageEntity carriage, BlockPos pos) {
        var previous = ControlsHandler.getContraption();
        var previousPos = ControlsHandler.getControlsPos();
        if (previous instanceof SnapshotCarriageEntity oldCarriage && previous != carriage && previousPos != null)
            release(oldCarriage, previousPos);
        ControlsHandler.startControlling(carriage, pos);
    }

    private static void release(SnapshotCarriageEntity carriage, BlockPos pos) {
        ControlsHandler.stopControlling();
        HexieNetworkHandler.sendToServer(new ReleaseNativeControlsPacket(carriage.getId(), pos));
    }
}
