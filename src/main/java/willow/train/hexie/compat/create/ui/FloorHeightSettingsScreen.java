package willow.train.hexie.compat.create.ui;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemMode;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemSliderView;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemView;
import willow.train.hexie.bridge.sync.SyncBridge.FloorHeightPacketAction;
import willow.train.hexie.bridge.world.WorldBlockBridge.BogeyFloorDataSettingView;

/** Brass slider for the setting selected by the held wrench. */
public final class FloorHeightSettingsScreen extends CenteredSemaphoreValueSettingsScreen {
    private final InteractionHand hand;
    private final int hotbarSlot;
    private final FloorSwitcherItemSliderView slider;
    private boolean confirmed;

    public FloorHeightSettingsScreen(InteractionHand hand, int hotbarSlot, int height) {
        this(hand, hotbarSlot, height, false);
    }
    public FloorHeightSettingsScreen(InteractionHand hand, int hotbarSlot, int height, boolean longitudinal) {
        this(hand, hotbarSlot, HexieBridges.toolOps().createFloorSwitcherItemSlider(
                longitudinal ? FloorSwitcherItemMode.LONGITUDINAL : FloorSwitcherItemMode.HEIGHT, height));
    }

    public FloorHeightSettingsScreen(InteractionHand hand, int hotbarSlot, FloorSwitcherItemSliderView slider) {
        super(BlockPos.ZERO, board(slider.mode()), new ValueSettings(0, coordinate(slider)), ignored -> {}, 0);
        this.hand = hand;
        this.hotbarSlot = hotbarSlot;
        this.slider = slider;
    }

    private static ValueSettingsBoard board(FloorSwitcherItemMode mode) {
        if (mode == FloorSwitcherItemMode.DOOR_DIRECTION)
            return new ValueSettingsBoard(Component.translatable("gui.hexie.door_direction.title"), 1, 1,
                    List.of(Component.translatable("gui.hexie.door_direction.axis")),
                    new ValueSettingsFormatter(settings -> Component.translatable(settings.value() == 0
                            ? "gui.hexie.door_direction.forward" : "gui.hexie.door_direction.reverse")));
        boolean longitudinal = mode == FloorSwitcherItemMode.LONGITUDINAL;
        return new ValueSettingsBoard(Component.translatable(longitudinal ? "gui.hexie.bogey_offset.title" : "gui.hexie.floor_height.title"),
                        setting(longitudinal).range(), setting(longitudinal).majorTick(), List.of(Component.translatable(longitudinal ? "gui.hexie.bogey_offset.axis" : "gui.hexie.floor_height.height")),
                        new ValueSettingsFormatter(settings -> {
                            if (longitudinal) return Component.literal(Integer.toString(setting(true).value(settings.value())));
                            int value = setting(false).value(settings.value());
                            return value == 24 ? Component.translatable("gui.hexie.floor_height.original")
                                    : Component.literal(value / 10 + "." + value % 10);
                        }));
    }

    private static int coordinate(FloorSwitcherItemSliderView slider) {
        return slider.mode() == FloorSwitcherItemMode.DOOR_DIRECTION ? slider.value()
                : setting(slider.mode() == FloorSwitcherItemMode.LONGITUDINAL).coordinate(slider.value());
    }

    private static BogeyFloorDataSettingView setting(boolean longitudinal) {
        return longitudinal ? HexieBridges.worldBlock().bogeyFloorData().longitudinalSetting() : HexieBridges.worldBlock().bogeyFloorData().heightSetting();
    }

    @Override protected void saveAndClose(double mouseX, double mouseY) {
        int coordinate = getClosestCoordinate((int) mouseX, (int) mouseY).value();
        // Never call ValueSettingsScreen's block-entity packet path for a portable tool.
        boolean door = slider.mode() == FloorSwitcherItemMode.DOOR_DIRECTION;
        int value = door ? coordinate : setting(slider.mode() == FloorSwitcherItemMode.LONGITUDINAL).value(coordinate);
        HexieBridges.sync().sendFloorHeight(hand, hotbarSlot, value,
                door ? FloorHeightPacketAction.CONFIRM_DOOR_DIRECTION : FloorHeightPacketAction.CONFIRM);
        confirmed = true;
        onClose();
    }

    @Override public void onClose() {
        if (!confirmed)
            HexieBridges.sync().sendFloorHeight(hand, hotbarSlot, slider.value(), FloorHeightPacketAction.CANCEL);
        if (minecraft != null && minecraft.player != null && minecraft.gameMode != null
                && minecraft.player.isUsingItem() && minecraft.player.getUseItem().getItem() instanceof FloorSwitcherItemView)
            minecraft.gameMode.releaseUsingItem(minecraft.player);
        super.onClose();
    }
}
