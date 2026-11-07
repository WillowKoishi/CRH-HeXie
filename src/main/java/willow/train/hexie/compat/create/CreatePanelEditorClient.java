package willow.train.hexie.compat.create;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Preserve the existing edit tools and exact hit; never send a moving placement/rotation request. */
@EventBusSubscriber(modid = "hexie", value = Dist.CLIENT)
public final class CreatePanelEditorClient {
    private CreatePanelEditorClient() {}
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (!event.isUseItem() || player == null || mc.level == null || mc.screen != null
                || player.isSpectator() || !player.mayBuild()) return;
        var hand = CreatePanelEditor.editorHand(player, event.getHand());
        if (hand == null) return;
        var picked = CreatePanelInteractionRay.pick(player);
        if (picked == null || !CreatePanelEditor.supports(new CreatePanelInteractionTarget.CapturedView(picked.carrier()),
                picked.hit().getBlockPos())) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(new CreatePanelEditorOpenPacket(
                picked.carrier().getId(), picked.carrier().getUUID(), hand, picked.hit()));
    }
}
