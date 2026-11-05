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

/** Brass slider for the setting selected by the held wrench. */
public final class FloorHeightSettingsScreen extends CenteredSemaphoreValueSettingsScreen {
    private final InteractionHand hand;
    private final int hotbarSlot;
    private final FloorSwitcherItem.Slider slider;
    private boolean confirmed;

    public FloorHeightSettingsScreen(InteractionHand hand, int hotbarSlot, int height) {
        this(hand, hotbarSlot, height, false);
    }
    public FloorHeightSettingsScreen(InteractionHand hand, int hotbarSlot, int height, boolean longitudinal) {
        this(hand, hotbarSlot, new FloorSwitcherItem.Slider(
                longitudinal ? FloorSwitcherItem.Mode.LONGITUDINAL : FloorSwitcherItem.Mode.HEIGHT, height));
    }

    public FloorHeightSettingsScreen(InteractionHand hand, int hotbarSlot, FloorSwitcherItem.Slider slider) {
        super(BlockPos.ZERO, board(slider.mode()), new ValueSettings(0, coordinate(slider)), ignored -> {}, 0);
        this.hand = hand;
        this.hotbarSlot = hotbarSlot;
        this.slider = slider;
    }

    private static ValueSettingsBoard board(FloorSwitcherItem.Mode mode) {
        if (mode == FloorSwitcherItem.Mode.DOOR_DIRECTION)
            return new ValueSettingsBoard(Component.translatable("gui.hexie.door_direction.title"), 1, 1,
                    List.of(Component.translatable("gui.hexie.door_direction.axis")),
                    new ValueSettingsFormatter(settings -> Component.translatable(settings.value() == 0
                            ? "gui.hexie.door_direction.forward" : "gui.hexie.door_direction.reverse")));
        boolean longitudinal = mode == FloorSwitcherItem.Mode.LONGITUDINAL;
        return new ValueSettingsBoard(Component.translatable(longitudinal ? "gui.hexie.bogey_offset.title" : "gui.hexie.floor_height.title"),
                        setting(longitudinal).range(), setting(longitudinal).majorTick(), List.of(Component.translatable(longitudinal ? "gui.hexie.bogey_offset.axis" : "gui.hexie.floor_height.height")),
                        new ValueSettingsFormatter(settings -> {
                            if (longitudinal) return Component.literal(Integer.toString(setting(true).value(settings.value())));
                            int value = setting(false).value(settings.value());
                            return value == 24 ? Component.translatable("gui.hexie.floor_height.original")
                                    : Component.literal(value / 10 + "." + value % 10);
                        }));
    }

    private static int coordinate(FloorSwitcherItem.Slider slider) {
        return slider.mode() == FloorSwitcherItem.Mode.DOOR_DIRECTION ? slider.value()
                : setting(slider.mode() == FloorSwitcherItem.Mode.LONGITUDINAL).coordinate(slider.value());
    }

    private static BogeyFloorData.Setting setting(boolean longitudinal) {
        return longitudinal ? BogeyFloorData.LONGITUDINAL_SETTING : BogeyFloorData.HEIGHT_SETTING;
    }

    @Override protected void saveAndClose(double mouseX, double mouseY) {
        int coordinate = getClosestCoordinate((int) mouseX, (int) mouseY).value();
        // Never call ValueSettingsScreen's block-entity packet path for a portable tool.
        boolean door = slider.mode() == FloorSwitcherItem.Mode.DOOR_DIRECTION;
        int value = door ? coordinate : setting(slider.mode() == FloorSwitcherItem.Mode.LONGITUDINAL).value(coordinate);
        PacketDistributor.sendToServer(new FloorHeightPacket(hand, hotbarSlot, value,
                door ? FloorHeightPacket.Action.CONFIRM_DOOR_DIRECTION : FloorHeightPacket.Action.CONFIRM));
        confirmed = true;
        onClose();
    }

    @Override public void onClose() {
        if (!confirmed)
            PacketDistributor.sendToServer(new FloorHeightPacket(hand, hotbarSlot, slider.value(), FloorHeightPacket.Action.CANCEL));
        if (minecraft != null && minecraft.player != null && minecraft.gameMode != null
                && minecraft.player.isUsingItem() && minecraft.player.getUseItem().getItem() instanceof FloorSwitcherItem)
            minecraft.gameMode.releaseUsingItem(minecraft.player);
        super.onClose();
    }
}
