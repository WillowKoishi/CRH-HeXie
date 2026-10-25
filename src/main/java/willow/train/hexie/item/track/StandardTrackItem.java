package willow.train.hexie.item.track;

import com.simibubi.create.content.trains.track.TrackBlockItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import willow.train.hexie.network.ServerHeldItemSync;

public class StandardTrackItem extends TrackBlockItem {
    public StandardTrackItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = super.useOn(context);
        ServerHeldItemSync.afterInteraction(context.getPlayer(), context.getHand());
        return result;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        ServerHeldItemSync.afterInteraction(player, hand);
        return result;
    }
}
