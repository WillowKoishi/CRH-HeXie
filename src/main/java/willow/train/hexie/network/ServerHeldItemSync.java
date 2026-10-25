package willow.train.hexie.network;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** Sends committed item components after NeoForge finishes the placement transaction. */
public final class ServerHeldItemSync {
    private ServerHeldItemSync() {}

    public static void afterInteraction(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sender))
            return;
        int slot = hand == InteractionHand.MAIN_HAND ? sender.getInventory().selected : Inventory.SLOT_OFFHAND;
        var server = sender.getServer();
        // tell always queues the task; execute can run inline, before a protection mod cancels placement.
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (sender.isRemoved())
                return;
            sender.getInventory().setChanged();
            sender.inventoryMenu.broadcastChanges();
            sender.connection.send(new ClientboundContainerSetSlotPacket(
                    ClientboundContainerSetSlotPacket.PLAYER_INVENTORY, 0, slot,
                    sender.getInventory().getItem(slot)));
        }));
    }
}
