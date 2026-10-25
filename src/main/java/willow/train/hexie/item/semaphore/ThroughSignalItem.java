package willow.train.hexie.item.semaphore;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.network.ServerHeldItemSync;
import willow.train.hexie.network.c2s.SignalPlacementFacingPacket;

public class ThroughSignalItem extends TrackTargetingBlockItem {
    public ThroughSignalItem(Block block, Properties properties) {
        super(block, properties, EdgePointType.SIGNAL);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (context.getLevel().isClientSide && player != null && !player.isShiftKeyDown()
                && context.getItemInHand().has(AllDataComponents.TRACK_TARGETING_ITEM_SELECTED_POS)
                && !(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof ITrackBlock)) {
            BlockPlaceContext placement = new BlockPlaceContext(context);
            // Prediction calls the item before sending vanilla's use-on packet, which carries no rotation.
            PacketDistributor.sendToServer(new SignalPlacementFacingPacket(placement.getClickedPos(),
                    context.getHand(), context.getHorizontalDirection().getOpposite()));
        }
        InteractionResult result = super.useOn(context);
        ServerHeldItemSync.afterInteraction(player, context.getHand());
        return result;
    }
}
