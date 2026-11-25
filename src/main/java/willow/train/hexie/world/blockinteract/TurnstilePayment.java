package willow.train.hexie.world.blockinteract;

import net.minecraft.world.item.ItemStack;
import willow.train.hexie.bridge.interaction.ToolOpsBridge;
import willow.train.hexie.core.util.general.ItemData;

/** The shared ticket transaction; each gate retains its own shape and opening animation. */
public final class TurnstilePayment {
    private TurnstilePayment() {}

    public static boolean accept(ItemStack stack, boolean exit) {
        var data = ItemData.getTag(stack);
        var station = data.getCompound("stationInfo");
        if (exit) {
            if (!(stack.getItem() instanceof ToolOpsBridge.TicketableView) || !station.getBoolean("enteredStation")) return false;
            station.putBoolean("enteredStation", false);
        } else {
            if (stack.getItem() instanceof ToolOpsBridge.TicketItemView) {
                var ticket = data.getCompound("ticketInfo");
                if (ticket.getBoolean("used")) return false;
                ticket.putBoolean("used", true);
                data.put("ticketInfo", ticket);
            } else if (stack.getItem() instanceof ToolOpsBridge.CardItemView) {
                var card = data.getCompound("cardInfo");
                long money = card.getLong("money");
                if (money < 100) return false;
                card.putLong("money", money - 100);
                data.put("cardInfo", card);
            } else return false;
            station.putBoolean("enteredStation", true);
        }
        data.put("stationInfo", station);
        ItemData.setTag(stack, data);
        return true;
    }
}
