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
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.network.c2s.FloorHeightPacket;

/** The same held-use brass slider as signal settings; its value belongs to the held tool. */
public final class FloorHeightSettingsScreen extends CenteredSemaphoreValueSettingsScreen {
    private final InteractionHand hand;
    private final int hotbarSlot;
    private final int initialHeight;
    private boolean confirmed;
    private final boolean longitudinal;

    public FloorHeightSettingsScreen(InteractionHand hand, int hotbarSlot, int height) {
        this(hand, hotbarSlot, height, false);
    }
    public FloorHeightSettingsScreen(InteractionHand hand, int hotbarSlot, int height, boolean longitudinal) {
        super(BlockPos.ZERO, new ValueSettingsBoard(Component.translatable(longitudinal ? "gui.hexie.bogey_offset.title" : "gui.hexie.floor_height.title"),
                        setting(longitudinal).range(), setting(longitudinal).majorTick(), List.of(Component.translatable(longitudinal ? "gui.hexie.bogey_offset.axis" : "gui.hexie.floor_height.height")),
                        new ValueSettingsFormatter(settings -> {
                            if (longitudinal) return Component.literal(Integer.toString(setting(true).value(settings.value())));
                            int value = setting(false).value(settings.value());
                            return value == 24 ? Component.translatable("gui.hexie.floor_height.original")
                                    : Component.literal(value / 10 + "." + value % 10);
                        })), new ValueSettings(0, setting(longitudinal).coordinate(height)), ignored -> {}, 0);
        this.hand = hand;
        this.hotbarSlot = hotbarSlot;
        this.initialHeight = height;
        this.longitudinal = longitudinal;
    }

    private static BogeyFloorData.Setting setting(boolean longitudinal) {
        return longitudinal ? BogeyFloorData.LONGITUDINAL_SETTING : BogeyFloorData.HEIGHT_SETTING;
    }

    @Override protected void saveAndClose(double mouseX, double mouseY) {
        int coordinate = getClosestCoordinate((int) mouseX, (int) mouseY).value();
        int height = setting(longitudinal).value(coordinate);
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
