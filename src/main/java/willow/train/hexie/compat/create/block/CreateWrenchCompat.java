package willow.train.hexie.compat.create.block;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import willow.train.hexie.core.api.interaction.HexieWrenchable;

public final class CreateWrenchCompat {
    private CreateWrenchCompat() {}

    public static void register() {
        HexieWrenchable.registerWrenchHooks(IWrenchable::playRotateSound, IWrenchable::playRemoveSound,
                CreateWrenchCompat::canRemove);
        NeoForge.EVENT_BUS.register(CreateWrenchCompat.class);
    }

    // The default dismantle path reaches this check after multiblock redirection.
    private static boolean canRemove(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), context.getPlayer());
        return !NeoForge.EVENT_BUS.post(event).isCanceled();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!AllItems.WRENCH.isIn(event.getItemStack())) return;
        var player = event.getEntity();
        if (player == null || player.isSpectator() || !player.mayBuild()) return;
        var useItem = event.getUseItem();
        if (useItem.isFalse() || (!useItem.isTrue() && player.getCooldowns().isOnCooldown(event.getItemStack().getItem())))
            return;

        var level = event.getLevel();
        var state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof HexieWrenchable wrenchable) || state.getBlock() instanceof IWrenchable)
            return;

        var context = new UseOnContext(level, player, event.getHand(), event.getItemStack(), event.getHitVec());
        var result = player.isShiftKeyDown() ? wrenchable.onSneakWrenched(state, context)
                : wrenchable.onWrenched(state, context);
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
