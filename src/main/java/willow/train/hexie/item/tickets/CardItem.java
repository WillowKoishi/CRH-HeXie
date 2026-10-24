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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import willow.train.hexie.Main;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class CardItem extends Item implements Ticketable {

    public CardItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public @NotNull ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        ItemData.updateTagElement(stack, "cardInfo", data -> data.putLong("money", 0));
        ItemData.updateTagElement(stack, "cardInfo", data -> data.putBoolean("valid", true));
        ItemData.updateTagElement(stack, "stationInfo", data -> data.putBoolean("enteredStation", true));
        ItemData.updateTagElement(stack, "stationInfo", data -> data.putString("station_in", "太原"));
        return stack;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable TooltipContext world,
            @NotNull List<Component> tooltip,
            @NotNull TooltipFlag context) {
        CompoundTag nbt = ItemData.getTagElement(stack, "cardInfo");
        CompoundTag nbt2 = ItemData.getTagElement(stack, "stationInfo");
        if (nbt != null && nbt2 != null) {
            if (nbt.contains("valid")) {
                if (nbt.getBoolean("valid")) {
                    if (nbt2.contains("enteredStation") && nbt2.contains("station_in")) {
                        tooltip.add(
                                Component.translatable("tooltip." + Main.MOD_ID + ".card.money")
                                        .copy()
                                        .append(nbt.getLong("money") + "")
                                        .withStyle(ChatFormatting.YELLOW));
                        if (nbt2.getBoolean("enteredStation")) {
                            tooltip.add(
                                    Component.translatable(
                                                    "tooltip." + Main.MOD_ID + ".ticket.station_in")
                                            .copy()
                                            .append(nbt2.getString("station_in"))
                                            .withStyle(ChatFormatting.AQUA));
                        }
                    }
                }
                if (nbt.getBoolean("valid")) {
                    if (nbt2.getBoolean("enteredStation")) {
                        tooltip.add(
                                Component.translatable("tooltip." + Main.MOD_ID + ".card.using")
                                        .withStyle(ChatFormatting.BLUE));
                    } else {
                        tooltip.add(
                                Component.translatable("tooltip." + Main.MOD_ID + ".ticket.valid")
                                        .withStyle(ChatFormatting.GREEN));
                    }
                } else {
                    tooltip.add(
                            Component.translatable("tooltip." + Main.MOD_ID + ".ticket.in_valid")
                                    .withStyle(ChatFormatting.RED));
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
