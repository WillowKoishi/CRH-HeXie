package willow.train.hexie.client.screens;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.item.FloorSwitcherItem;
import willow.train.hexie.network.c2s.FloorHeightPacket;

/** The same held-use brass slider as signal settings; its value belongs to the held tool. */
public final class FloorHeightSettingsScreen extends CenteredSemaphoreValueSettingsScreen {
    private final InteractionHand hand;
    private final int hotbarSlot;
    private final int initialHeight;
    private boolean confirmed;

    public FloorHeightSettingsScreen(InteractionHand hand, int hotbarSlot, int height) {
        super(BlockPos.ZERO, new ValueSettingsBoard(Component.translatable("gui.hexie.floor_height.title"),
                        20, 5, List.of(Component.translatable("gui.hexie.floor_height.height")),
                        new ValueSettingsFormatter(settings -> {
                            int value = settings.value() + 10;
                            return value == 24 ? Component.translatable("gui.hexie.floor_height.original")
                                    : Component.literal(value / 10 + "." + value % 10);
                        })), new ValueSettings(0, Math.clamp(height, 10, 30) - 10), ignored -> {}, 0);
        this.hand = hand;
        this.hotbarSlot = hotbarSlot;
        this.initialHeight = height;
    }

    @Override protected void saveAndClose(double mouseX, double mouseY) {
        int height = Math.clamp(getClosestCoordinate((int) mouseX, (int) mouseY).value() + 10, 10, 30);
        // Never call ValueSettingsScreen's block-entity packet path for a portable tool.
        PacketDistributor.sendToServer(new FloorHeightPacket(hand, hotbarSlot, height));
        confirmed = true;
        onClose();
    }

    @Override public void onClose() {
        if (!confirmed)
            PacketDistributor.sendToServer(new FloorHeightPacket(hand, hotbarSlot, initialHeight, FloorHeightPacket.Action.CANCEL));
        if (minecraft != null && minecraft.player != null && minecraft.gameMode != null
                && minecraft.player.isUsingItem() && minecraft.player.getUseItem().getItem() instanceof FloorSwitcherItem)
            minecraft.gameMode.releaseUsingItem(minecraft.player);
        super.onClose();
    }
}
