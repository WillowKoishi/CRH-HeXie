package willow.train.hexie.blocks.signs;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.item.FloorSwitcherItem;
import willow.train.hexie.menu.CarriageNumberMenu;

/** The HeXie wrench edits the small screen; attachment placement and swapping stay separate. */
@EventBusSubscriber(modid = "hexie")
public final class CarriageNumberInteraction {
    private CarriageNumberInteraction() {}
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (!(event.getItemStack().getItem() instanceof FloorSwitcherItem)) {
            if (event.getHand() != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()
                    || !(player.getOffhandItem().getItem() instanceof FloorSwitcherItem)) return;
        }
        var level = event.getLevel();
        if (level != player.level()) return;
        var root = PartStructures.resolve(level, event.getPos());
        if (root == null || !PassengerDisplayPanel.supports(root.state().getBlock())
                || !root.state().getValue(PanelDisplayMount.PROPERTY).hasNumber()) return;
        event.setCanceled(true);
        if (!player.mayBuild() || player.isSpectator()) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        FloorSwitcherItem.cancelPending(player);
        boolean opened = level.isClientSide || player instanceof ServerPlayer server
                && CarriageNumberMenu.openStatic(server, level.getBlockEntity(root.pos()));
        event.setCancellationResult(opened ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.FAIL);
    }
}
