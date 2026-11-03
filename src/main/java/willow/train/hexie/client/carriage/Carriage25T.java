package willow.train.hexie.client.carriage;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.item.CarriagePartCatalog;

/** Legacy picker entry point; definitions are shared with the creative inventory. */
public class Carriage25T {
    public static List<ItemStack> getList() { return CarriagePartCatalog.palette("Carriage25T"); }
}
