package willow.train.hexie.item.tickets;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import willow.train.hexie.compat.ItemData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import org.jetbrains.annotations.Nullable;

import willow.train.hexie.Main;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class TicketItem extends Item implements Ticketable {
    public TicketItem(Properties settings) {
        super(settings);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        ItemData.updateTagElement(stack, "ticketInfo", data -> data.putBoolean("valid", true));
        ItemData.updateTagElement(stack, "ticketInfo", data -> data.putInt("money", 0));
        ItemData.updateTagElement(stack, "stationInfo", data -> data.putBoolean("enteredStation", true));
        ItemData.updateTagElement(stack, "stationInfo", data -> data.putString("station_in", "北京"));
        ItemData.updateTagElement(stack, "stationInfo", data -> data.putString("station_out", "上海虹桥"));
        return stack;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, @Nullable TooltipContext world, List<Component> tooltip, TooltipFlag context) {
        CompoundTag nbt = ItemData.getTagElement(stack, "ticketInfo");
        CompoundTag nbt2 = ItemData.getTagElement(stack, "stationInfo");
        if (nbt != null && nbt2 != null) {
            if (nbt.contains("valid")) {
                if (nbt2.contains("station_in")
                        && nbt2.contains("station_out")
                        && nbt2.contains("enteredStation")) {
                    if (nbt2.getBoolean("enteredStation")) {
                        if (nbt.getBoolean("valid")) {
                            tooltip.add(
                                    Component.translatable(
                                                    "tooltip." + Main.MOD_ID + ".ticket.station_in")
                                            .copy()
                                            .append(nbt2.getString("station_in"))
                                            .withStyle(
                                                    ChatFormatting.AQUA,
                                                    ChatFormatting.STRIKETHROUGH));
                            tooltip.add(
                                    Component.translatable(
                                                    "tooltip."
                                                            + Main.MOD_ID
                                                            + ".ticket.station_out")
                                            .copy()
                                            .append(nbt2.getString("station_out"))
                                            .withStyle(ChatFormatting.GOLD));
                        } else {
                            tooltip.add(
                                    Component.translatable(
                                                    "tooltip." + Main.MOD_ID + ".ticket.station_in")
                                            .copy()
                                            .append(nbt2.getString("station_in"))
                                            .withStyle(
                                                    ChatFormatting.AQUA,
                                                    ChatFormatting.STRIKETHROUGH));
                            tooltip.add(
                                    Component.translatable(
                                                    "tooltip."
                                                            + Main.MOD_ID
                                                            + ".ticket.station_out")
                                            .copy()
                                            .append(nbt2.getString("station_out"))
                                            .withStyle(
                                                    ChatFormatting.GOLD,
                                                    ChatFormatting.STRIKETHROUGH));
                        }
                    } else if (nbt2.getBoolean("valid")) {
                        tooltip.add(
                                Component.translatable(
                                                "tooltip." + Main.MOD_ID + ".ticket.station_in")
                                        .copy()
                                        .append(nbt2.getString("station_in"))
                                        .withStyle(ChatFormatting.AQUA));
                        tooltip.add(
                                Component.translatable(
                                                "tooltip." + Main.MOD_ID + ".ticket.station_out")
                                        .copy()
                                        .append(nbt2.getString("station_out"))
                                        .withStyle(ChatFormatting.GOLD));
                    }
                    if (nbt.getBoolean("valid")) {
                        if (nbt2.getBoolean("enteredStation")) {
                            tooltip.add(
                                    Component.translatable("tooltip." + Main.MOD_ID + ".card.using")
                                            .withStyle(ChatFormatting.BLUE));
                        } else {
                            tooltip.add(
                                    Component.translatable(
                                                    "tooltip." + Main.MOD_ID + ".ticket.valid")
                                            .withStyle(ChatFormatting.GREEN));
                        }
                    } else {
                        if (nbt2.getBoolean("enteredStation")) {
                            tooltip.add(
                                    Component.translatable(
                                                    "tooltip." + Main.MOD_ID + ".ticket.exited")
                                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                        } else {
                            tooltip.add(
                                    Component.translatable(
                                                    "tooltip." + Main.MOD_ID + ".ticket.in_valid")
                                            .withStyle(ChatFormatting.RED));
                        }
                    }
                }
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.PASS;
        AtomicReference<InteractionResult> result = new AtomicReference<>(InteractionResult.PASS);
        return result.get();
    }
}
