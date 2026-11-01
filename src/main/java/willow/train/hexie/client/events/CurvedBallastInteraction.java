package willow.train.hexie.client.events;

import com.simibubi.create.content.trains.track.TrackBlockOutline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.track.standard.TrackBallast;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.network.c2s.TrackBallastPacket;

/** Runs after the cab's HIGHEST input capture and before Create's NORMAL curve interaction. */
@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class CurvedBallastInteraction {
    private CurvedBallastInteraction() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var selection = TrackBlockOutline.result;
        if (minecraft.screen != null || !event.isUseItem() || selection == null || player == null
                || !TrackBallast.canPaint(player, player.getMainHandItem())) return;
        var curve = selection.blockEntity().getConnections().get(selection.loc().curveTarget());
        if (curve == null || curve.getMaterial() != TrackMaterialInit.STANDARD) return;
        PacketDistributor.sendToServer(new TrackBallastPacket(selection.blockEntity().getBlockPos(),
                selection.loc().curveTarget(), selection.loc().segment()));
        player.swing(InteractionHand.MAIN_HAND);
        event.setCanceled(true);
    }
}
