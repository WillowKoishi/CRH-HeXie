package willow.train.hexie.interaction.item.tools;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemMode;
import willow.train.hexie.bridge.world.WorldBlockBridge.BogeyFloorDataOperations;

// split from willow/train/hexie/item/FloorSwitcherItem.java
final class FloorSwitcherSettings {
    public static FloorSwitcherItemMode mode(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.getBoolean("ReverseMode")) return FloorSwitcherItemMode.REVERSE;
        return data.getBoolean("LongitudinalMode")
                ? FloorSwitcherItemMode.LONGITUDINAL : FloorSwitcherItemMode.HEIGHT;
    }
    public static boolean valid(ItemStack stack, int value) {
        return switch (mode(stack)) {
            case HEIGHT -> HexieBridges.worldBlock().bogeyFloorData().valid(value);
            case LONGITUDINAL -> HexieBridges.worldBlock().bogeyFloorData().validLongitudinal(value);
            default -> false;
        };
    }
    public static void switchMode(Player player, InteractionHand hand) {
        FloorSwitcherTargets.cancelPending(player);
        if (player.level().isClientSide) return;
        var stack = player.getItemInHand(hand);
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var previous = mode(stack);
        data.putBoolean("LongitudinalMode", previous == FloorSwitcherItemMode.HEIGHT);
        data.putBoolean("ReverseMode", previous == FloorSwitcherItemMode.LONGITUDINAL);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        player.displayClientMessage(Component.translatable(switch (mode(stack)) {
            case LONGITUDINAL -> "gui.hexie.bogey_offset.title";
            case REVERSE -> "gui.hexie.bogey_reverse.title";
            default -> "gui.hexie.floor_height.title";
        }), true);
        if (player instanceof net.minecraft.server.level.ServerPlayer server)
            HexieBridges.sync().serverHeldItemSync().afterInteraction(server, hand);
    }

    public static int height(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (mode(stack) == FloorSwitcherItemMode.LONGITUDINAL) return HexieBridges.worldBlock().bogeyFloorData().longitudinal(data);
        int height = data.getInt(BogeyFloorDataOperations.KEY);
        return HexieBridges.worldBlock().bogeyFloorData().valid(height) ? height : BogeyFloorDataOperations.DEFAULT_HEIGHT;
    }

    public static void setHeight(ItemStack stack, int height) {
        if (!valid(stack, height)) throw new IllegalArgumentException("Invalid wrench setting: " + height);
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putInt(mode(stack) == FloorSwitcherItemMode.HEIGHT ? BogeyFloorDataOperations.KEY : BogeyFloorDataOperations.LONGITUDINAL_KEY, height);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    public static Component message(int height) {
        return height == 24 ? Component.translatable("gui.hexie.floor_height.original")
                : Component.translatable("item.hexie.harmony_wrench.height", height / 10 + "." + height % 10);
    }
}
