package willow.train.hexie.render.init;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;

public final class SnapshotControlsClient {
    private SnapshotControlsClient() {}

    public static boolean click(SnapshotCarriageView carriage, BlockPos pos, Player player) {
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

    public static void startConfirmed(SnapshotCarriageView carriage, BlockPos pos) {
        var previous = ControlsHandler.getContraption();
        var previousPos = ControlsHandler.getControlsPos();
        if (previous instanceof SnapshotCarriageView oldCarriage && previous != carriage && previousPos != null)
            release(oldCarriage, previousPos);
        ControlsHandler.startControlling(carriage.asEntity(), pos);
    }

    private static void release(SnapshotCarriageView carriage, BlockPos pos) {
        ControlsHandler.stopControlling();
        HexieBridges.sync().sendReleaseNativeControls(carriage.asEntity().getId(), pos);
    }
}
