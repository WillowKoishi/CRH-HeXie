package willow.train.hexie.compat.create.ui;

import com.simibubi.create.content.trains.track.TrackBlockOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.railway.RailwayDefinition.Bed;

/** Runs after the cab's HIGHEST input capture and before Create's NORMAL curve interaction. */
public final class CurvedBallastInteraction {
    private CurvedBallastInteraction() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var selection = TrackBlockOutline.result;
        if (minecraft.screen != null || !event.isUseItem() || selection == null || player == null
                || !HexieBridges.worldBlock().trackBallast().canPaint(player, player.getMainHandItem())) return;
        var curve = selection.blockEntity().getConnections().get(selection.loc().curveTarget());
        if (curve == null || HexieBridges.createTrain().createRailwayBackend().bed(curve.getMaterial()) != Bed.BALLAST) return;
        HexieBridges.sync().sendTrackBallast(selection.blockEntity().getBlockPos(),
                selection.loc().curveTarget(), selection.loc().segment());
        player.swing(InteractionHand.MAIN_HAND);
        event.setCanceled(true);
    }
}
