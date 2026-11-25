package willow.train.hexie.ui.flow;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import willow.train.hexie.bridge.HexieBridges;

/** Legacy picker entry point; definitions are shared with the creative inventory. */
public class Marshalled25Series {
    public static List<ItemStack> getList() { return HexieBridges.toolOps().carriagePartCatalog().palette("Marshalled25Series"); }
}
