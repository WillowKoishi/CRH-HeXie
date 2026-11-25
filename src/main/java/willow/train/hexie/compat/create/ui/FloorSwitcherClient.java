package willow.train.hexie.compat.create.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemOperations;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemView;
import willow.train.hexie.bridge.sync.SyncBridge.FloorHeightPacketAction;

/** Opens one settings slider per held press and leaves short-press application to the item. */
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
                || !(player.getUseItem().getItem() instanceof FloorSwitcherItemView)
                || player.getTicksUsingItem() < FloorSwitcherItemOperations.HOLD_TICKS) return;
        var hand = player.getUsedItemHand();
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        var slider = HexieBridges.toolOps().floorSwitcherItem().configuration(player);
        waitingForRelease = true;
        // Keep the target for confirmation, while preventing a short-release application during screen opening.
        HexieBridges.sync().sendFloorHeight(hand, slot, slider.value(), FloorHeightPacketAction.OPEN);
        minecraft.setScreen(new FloorHeightSettingsScreen(hand, slot, slider));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventHeldPressRestart(InputEvent.InteractionKeyMappingTriggered event) {
        if (!waitingForRelease || !event.isUseItem()) return;
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.getItemInHand(event.getHand()).getItem() instanceof FloorSwitcherItemView)) return;
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
