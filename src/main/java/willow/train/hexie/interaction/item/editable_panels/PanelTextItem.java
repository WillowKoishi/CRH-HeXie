package willow.train.hexie.interaction.item.editable_panels;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.PanelTextItemView;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2PartEntityView;
import willow.train.hexie.bridge.world.PartBlockBridge.VehicleMarkingSurfaceView;

public final class PanelTextItem extends Item implements PanelTextItemView {
    public PanelTextItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("item.hexie.panel_text.tooltip"));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var root = HexieBridges.partBlock().partStructures().resolve(level, context.getClickedPos());
        var state = root == null ? level.getBlockState(context.getClickedPos()) : root.state();
        if (!(state.getBlock() instanceof VehicleMarkingSurfaceView panel)) return InteractionResult.PASS;
        var player = context.getPlayer();
        if (player == null || player.level() != level || !player.mayBuild() || player.isSpectator())
            return InteractionResult.FAIL;
        var pos = root == null ? panel.markingRoot(level, context.getClickedPos(), state) : root.pos();
        if (!(level.getBlockEntity(pos) instanceof CRH2PartEntityView entity)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server)
            HexieBridges.menu().panelTextMenu().open(server, entity, panel.markingSlot(state, pos, context.getClickLocation()), context.getHand());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
