package willow.train.hexie.blocks.signs;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.item.PassengerDisplayItem;

/** The original carriage panel stays installed; only its attachment state changes. */
@EventBusSubscriber(modid = "hexie")
public final class PassengerDisplayInstallation {
    private PassengerDisplayInstallation() {}

    @SubscribeEvent
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (event.getLevel() != player.level()) return;
        if (event.getItemStack().getItem() instanceof PassengerDisplayItem item) {
            var root = PartStructures.resolve(event.getLevel(), event.getPos());
            if (root == null || !PassengerDisplayPanel.supports(root.state().getBlock())) return;
            event.setCanceled(true);
            event.setCancellationResult(install(new UseOnContext(player, event.getHand(), event.getHitVec()), item.number()));
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty() || player.isSpectator() || !player.mayBuild()) return;
        var root = PartStructures.resolve(event.getLevel(), event.getPos());
        if (root == null || !PassengerDisplayPanel.supports(root.state().getBlock())
                || root.state().getValue(PanelDisplayMount.PROPERTY) == PanelDisplayMount.NONE) return;
        event.setCanceled(true);
        boolean changed = change(event.getLevel(), root,
                root.state().setValue(PanelDisplayMount.PROPERTY, root.state().getValue(PanelDisplayMount.PROPERTY).swap()),
                new UseOnContext(player, event.getHand(), event.getHitVec()));
        event.setCancellationResult(changed ? InteractionResult.sidedSuccess(event.getLevel().isClientSide)
                : InteractionResult.FAIL);
    }

    public static InteractionResult install(UseOnContext context, boolean number) {
        var player = context.getPlayer();
        if (player == null || player.isSpectator() || !player.mayBuild()
                || context.getLevel() != player.level()) return InteractionResult.FAIL;
        var level = context.getLevel();
        var root = PartStructures.resolve(level, context.getClickedPos());
        if (root == null || !PassengerDisplayPanel.supports(root.state().getBlock())) return InteractionResult.PASS;
        var current = root.state().getValue(PanelDisplayMount.PROPERTY);
        var next = current.add(number);
        var column = PanelDisplayColumn.fromClick(root.state(), root.pos(), context.getClickedPos());
        if (column == null) return InteractionResult.FAIL;
        if (current == next && PanelDisplayColumn.get(root.state()) == column) return InteractionResult.FAIL;
        var installed = root.state().setValue(PanelDisplayMount.PROPERTY, next).setValue(PanelDisplayColumn.PROPERTY, column);
        if (!change(level, root, installed, context)) return InteractionResult.FAIL;
        if (current != next && !level.isClientSide && !player.isCreative()) context.getItemInHand().shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean change(Level level, PartStructures.Root root, BlockState next, UseOnContext context) {
        boolean changed = PartInstallation.replace(level,
                root.structure().partCells(level, root.pos(), root.state()),
                root.structure().partCells(level, root.pos(), next), pos -> false,
                pos -> level.mayInteract(context.getPlayer(), pos)
                        && context.getPlayer().mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()));
        if (changed && !level.isClientSide)
            level.playSound(null, root.pos(), SoundEvents.METAL_PLACE, SoundSource.BLOCKS, .6f, 1.1f);
        return changed;
    }

    public static boolean isRoot(BlockState state) {
        return !state.hasProperty(willow.train.hexie.blocks.emu.crh2a.CRH2PartBlock.PART)
                || state.getValue(willow.train.hexie.blocks.emu.crh2a.CRH2PartBlock.PART) == 0;
    }

}
