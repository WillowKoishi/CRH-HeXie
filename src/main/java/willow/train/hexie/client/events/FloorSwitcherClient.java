package willow.train.hexie.client.events;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.Main;
import willow.train.hexie.client.screens.FloorHeightSettingsScreen;
import willow.train.hexie.item.FloorSwitcherItem;
import willow.train.hexie.network.c2s.FloorHeightPacket;

/** Opens one settings slider per held press and leaves short-press application to the item. */
@EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class FloorSwitcherClient {
    private static boolean waitingForRelease;

    private FloorSwitcherClient() {}

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null) {
            waitingForRelease = false;
            return;
        }
        if (waitingForRelease) {
            if (!(minecraft.screen instanceof FloorHeightSettingsScreen) && !useKeyPhysicallyDown(minecraft))
                waitingForRelease = false;
            return;
        }
        if (minecraft.screen != null || !player.isUsingItem()
                || !(player.getUseItem().getItem() instanceof FloorSwitcherItem)
                || player.getTicksUsingItem() < FloorSwitcherItem.HOLD_TICKS) return;
        var hand = player.getUsedItemHand();
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        int height = FloorSwitcherItem.configurationHeight(player);
        waitingForRelease = true;
        // Keep the target for confirmation, while preventing a short-release application during screen opening.
        PacketDistributor.sendToServer(new FloorHeightPacket(hand, slot, height, FloorHeightPacket.Action.OPEN));
        minecraft.setScreen(new FloorHeightSettingsScreen(hand, slot, height));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventHeldPressRestart(InputEvent.InteractionKeyMappingTriggered event) {
        if (!waitingForRelease || !event.isUseItem()) return;
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.getItemInHand(event.getHand()).getItem() instanceof FloorSwitcherItem)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    private static boolean useKeyPhysicallyDown(Minecraft minecraft) {
        var key = minecraft.options.keyUse.getKey();
        long window = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE)
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM)
            return InputConstants.isKeyDown(window, key.getValue());
        return minecraft.options.keyUse.isDown();
    }
}
