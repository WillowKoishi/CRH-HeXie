package willow.train.hexie.blocks.turnstile;

import net.minecraft.world.item.ItemStack;
import willow.train.hexie.compat.ItemData;
import willow.train.hexie.item.tickets.CardItem;
import willow.train.hexie.item.tickets.TicketItem;
import willow.train.hexie.item.tickets.Ticketable;

/** The shared ticket transaction; each gate retains its own shape and opening animation. */
public final class TurnstilePayment {
    private TurnstilePayment() {}

    public static boolean accept(ItemStack stack, boolean exit) {
        var data = ItemData.getTag(stack);
        var station = data.getCompound("stationInfo");
        if (exit) {
            if (!(stack.getItem() instanceof Ticketable) || !station.getBoolean("enteredStation")) return false;
            station.putBoolean("enteredStation", false);
        } else {
            if (stack.getItem() instanceof TicketItem) {
                var ticket = data.getCompound("ticketInfo");
                if (ticket.getBoolean("used")) return false;
                ticket.putBoolean("used", true);
                data.put("ticketInfo", ticket);
            } else if (stack.getItem() instanceof CardItem) {
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
