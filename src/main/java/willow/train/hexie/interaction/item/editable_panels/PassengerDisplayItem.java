package willow.train.hexie.interaction.item.editable_panels;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.PassengerDisplayItemView;

/** The two inventory blocks install into a carriage panel instead of replacing its material. */
public final class PassengerDisplayItem extends BlockItem implements PassengerDisplayItemView {
    private final boolean number;
    public PassengerDisplayItem(Block block, Properties properties, boolean number) {
        super(block, properties); this.number = number;
    }
    public boolean number() { return number; }
    @Override public InteractionResult useOn(UseOnContext context) {
        return HexieBridges.partBlock().passengerDisplayInstallation().install(context, number);
    }
}
