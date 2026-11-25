package willow.train.hexie.compat.create.track;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackBlockItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import willow.train.hexie.bridge.HexieBridges;

public class StandardTrackItem extends TrackBlockItem {
    public StandardTrackItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        if (state != null && StandardTrackPavingPreset.INSTANCE.isStandardTrack(context.getItemInHand())
                && StandardTrackPavingPreset.getBlock(context.getItemInHand()) != Blocks.COBBLESTONE)
            return state.setValue(TrackBlock.HAS_BE, true);
        return state;
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player,
                                                ItemStack stack, BlockState state) {
        boolean updated = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!StandardTrackPavingPreset.INSTANCE.isStandardTrack(stack))
            return updated;
        Block material = StandardTrackPavingPreset.getBlock(stack);
        // Initialize on both sides before BlockItem completes the predicted placement.
        if (material != Blocks.COBBLESTONE
                && level.getBlockEntity(pos) instanceof TrackBlockEntity track) {
            TrackBallast.paint(track, null, material.defaultBlockState());
            return true;
        }
        return updated;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var placement = StandardTrackBallastPlacement.begin(context);
        InteractionResult result = null;
        try {
            result = super.useOn(context);
        } finally {
            StandardTrackBallastPlacement.end(placement, result);
        }
        HexieBridges.sync().serverHeldItemSync().afterInteraction(context.getPlayer(), context.getHand());
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (StandardTrackPavingPreset.INSTANCE.isStandardTrack(stack))
            tooltip.add(Component.translatable("tooltip.hexie.standard_track.ballast",
                    StandardTrackPavingPreset.INSTANCE.getBlockStack(stack).getHoverName()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (StandardTrackPavingPreset.INSTANCE.isStandardTrack(stack)
                && player.isShiftKeyDown() && isLookingAtAir(level, player)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inventory, viewer) -> HexieBridges.menu().standardTrackPavingMenu().create(id, inventory, hand),
                        Component.translatable("gui.hexie.standard_track_paving.title")),
                        buffer -> buffer.writeEnum(hand));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        HexieBridges.sync().serverHeldItemSync().afterInteraction(player, hand);
        return result;
    }

    private static boolean isLookingAtAir(Level level, Player player) {
        return Item.getPlayerPOVHitResult(level, player, net.minecraft.world.level.ClipContext.Fluid.NONE)
                        .getType() == HitResult.Type.MISS
                && player.pick(player.entityInteractionRange(), 0.0F, false).getType() == HitResult.Type.MISS;
    }
}
