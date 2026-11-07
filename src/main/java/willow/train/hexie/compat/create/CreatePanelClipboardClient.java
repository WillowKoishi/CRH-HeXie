package willow.train.hexie.compat.create;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keep the native Create gesture and picking, adding the exact attachment hit to read-only copy. */
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class CreatePanelClipboardClient {
    private CreatePanelClipboardClient() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || player.isSpectator() || player.isShiftKeyDown()
                || !event.isUseItem() && !event.isAttack()) return;
        var hand = CreatePanelClipboard.clipboardHand(player,
                event.isUseItem() ? event.getHand() : InteractionHand.MAIN_HAND);
        if (hand == null) return;
        var picked = CreatePanelInteractionRay.pick(player);
        if (picked == null || !CreatePanelClipboard.panel(new CreatePanelInteractionTarget.CapturedView(picked.carrier()), picked.hit().getBlockPos())) return;
        // Moving clipboard paste stays frozen; content editors have a separate menu session.
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isUseItem()) PacketDistributor.sendToServer(new CreatePanelClipboardCopyPacket(
                picked.carrier().getId(), picked.carrier().getUUID(), hand, picked.hit()));
    }
}
