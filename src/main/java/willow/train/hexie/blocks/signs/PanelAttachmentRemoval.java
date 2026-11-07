package willow.train.hexie.blocks.signs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import willow.train.hexie.Main;
import willow.train.hexie.blockentity.CRH2PartEntity;
import willow.train.hexie.blocks.carriage.PanelConversion;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.blocks.parts.PartStructure;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.init.item.EditablePanelItemInit;
import willow.train.hexie.init.item.LogoItemInit;

/** White-brush removal is one transaction on the complete, unassembled panel. */
@EventBusSubscriber(modid = "hexie")
public final class PanelAttachmentRemoval {
    private PanelAttachmentRemoval() {}

    // Old sign blocks consume item interactions before Item.useOn can run.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        var hand = event.getHand();
        if (!event.getItemStack().is(EditablePanelItemInit.Brush.get())) {
            // A legacy sign's empty-hand flip would otherwise consume the main-hand click
            // before Minecraft gets a chance to use a white brush in the offhand.
            if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()
                    || !player.getOffhandItem().is(EditablePanelItemInit.Brush.get())) return;
            hand = InteractionHand.OFF_HAND;
        }
        if (event.getLevel() != event.getEntity().level()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        var result = remove(new UseOnContext(player, hand, event.getHitVec()));
        if (result == InteractionResult.PASS) return;
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    public static InteractionResult remove(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        if (player == null || player.level() != level || player.isSpectator() || !player.mayBuild())
            return InteractionResult.FAIL;
        var root = PartStructures.resolve(level, context.getClickedPos());
        var pos = root == null ? context.getClickedPos() : root.pos();
        var state = root == null ? level.getBlockState(pos) : root.state();
        var next = plainPanel(state);
        var markings = level.getBlockEntity(pos) instanceof CRH2PartEntity panel ? panel : null;
        boolean hasMarkings = markings != null && markings.hasMarkings();
        if (next == state && !hasMarkings) return InteractionResult.PASS;

        var recovered = recoverable(state, markings);
        var previousCells = root == null ? Map.of(pos, state) : root.structure().partCells(level, pos, state);
        var nextCells = next.getBlock() instanceof PartStructure part
                ? part.partCells(level, pos, next) : Map.of(pos, next);
        boolean changed = PartInstallation.replace(level, previousCells, nextCells, at -> false,
                at -> level.mayInteract(player, at)
                        && player.mayUseItemAt(at, context.getClickedFace(), context.getItemInHand()),
                () -> {
                    if (markings != null) markings.clearMarkings();
                    if (level.getBlockEntity(pos) instanceof willow.train.hexie.api.display.PassengerDisplayHost host)
                        host.setCarriageNumber("");
                });
        if (!changed) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            if (!player.isCreative()) for (var stack : recovered)
                if (!player.addItem(stack)) player.drop(stack, false);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, .6f, 1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static BlockState plainPanel(BlockState state) {
        BlockState plain = state;
        if (state.getBlock() instanceof TrainPanelBlock)
            plain = Main.PANEL_TYPE_UTIL.matchBottomPanel2Block(state.getValue(TrainPanelBlock.TYPE)).defaultBlockState();
        else if (state.getBlock() instanceof LacqueredBoardBlock)
            plain = Main.PANEL_TYPE_UTIL.matchBottomPanel2Block(state.getValue(LacqueredBoardBlock.TYPE)).defaultBlockState();
        else if (state.getBlock() instanceof TrainNoBlock)
            plain = Main.PANEL_TYPE_UTIL.matchUpperPanel2Block(state.getValue(TrainNoBlock.TYPE)).defaultBlockState();
        else if (state.getBlock() instanceof TrainSpeedSignBlock)
            plain = Main.PANEL_TYPE_UTIL.matchSlabPanel2Block(state.getValue(TrainSpeedSignBlock.TYPE)).defaultBlockState();
        plain = PanelConversion.preservePlacement(state, plain);
        if (PassengerDisplayPanel.supports(plain.getBlock()) && plain.hasProperty(PanelDisplayMount.PROPERTY)) {
            plain = plain.setValue(PanelDisplayMount.PROPERTY, PanelDisplayMount.NONE);
            if (plain.hasProperty(PanelDisplayColumn.PROPERTY))
                plain = plain.setValue(PanelDisplayColumn.PROPERTY, PanelDisplayColumn.ROOT);
        }
        return plain;
    }

    private static List<ItemStack> recoverable(BlockState state, CRH2PartEntity markings) {
        var result = new ArrayList<>(PassengerDisplayPanel.withDisplayDrops(state, List.of()));
        if (state.getBlock() instanceof LacqueredBoardBlock)
            result.add(EditablePanelItemInit.LACQUERED_BOARD.get().getDefaultInstance());
        if (state.getBlock() instanceof TrainNoBlock)
            result.add(EditablePanelItemInit.Sticker.get().getDefaultInstance());
        if (markings != null) for (int slot = 0; slot < 2; slot++) {
            if (markings.harmony(slot) != 0) result.add(LogoItemInit.HARMONY_LOGO.get().getDefaultInstance());
            if (!markings.panelText(slot).text().isEmpty()) result.add(LogoItemInit.PANEL_TEXT.get().getDefaultInstance());
        }
        return result;
    }
}
