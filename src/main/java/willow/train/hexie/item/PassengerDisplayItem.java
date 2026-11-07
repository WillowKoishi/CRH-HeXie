package willow.train.hexie.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.blocks.signs.PassengerDisplayInstallation;

/** The two inventory blocks install into a carriage panel instead of replacing its material. */
public final class PassengerDisplayItem extends BlockItem {
    private final boolean number;
    public PassengerDisplayItem(Block block, Properties properties, boolean number) {
        super(block, properties); this.number = number;
    }
    public boolean number() { return number; }
    @Override public InteractionResult useOn(UseOnContext context) {
        return PassengerDisplayInstallation.install(context, number);
    }
}
