package willow.train.hexie.blocks.signs;

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
import willow.train.hexie.api.display.PassengerDisplayHost;
import willow.train.hexie.blocks.carriage.PanelConversion;
import willow.train.hexie.blocks.parts.PartInstallation;
import willow.train.hexie.blocks.parts.PartStructure;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.blocks.parts.PanelText;
import willow.train.hexie.blocks.parts.VehicleMarkings;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.init.item.EditablePanelItemInit;
import willow.train.hexie.init.item.LogoItemInit;

/** Remove only the clicked attachment, as one transaction on the unassembled panel. */
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
        var hit = new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),
                context.getClickedFace(), context.getClickedPos(), false);
        var target = PanelAttachmentHit.resolve(level, context.getClickedPos(), hit);
        if (target.kind() == PanelAttachmentHit.Kind.NONE) return InteractionResult.PASS;
        var pos = target.pos();
        var state = target.state();
        var root = PartStructures.resolve(level, pos);
        var markings = level.getBlockEntity(pos) instanceof VehicleMarkings content ? content : null;
        var next = state;
        ItemStack recovered = ItemStack.EMPTY;
        switch (target.kind()) {
            case SIGN, NUMBER -> {
                boolean number = target.kind() == PanelAttachmentHit.Kind.NUMBER;
                var mount = state.getValue(PanelDisplayMount.PROPERTY).remove(number);
                next = state.setValue(PanelDisplayMount.PROPERTY, mount);
                if (mount == PanelDisplayMount.NONE)
                    next = next.setValue(PanelDisplayColumn.PROPERTY, PanelDisplayColumn.ROOT);
                recovered = (number ? BlockInit.CRH2A_CARRIAGE_NUMBER_DISPLAY : BlockInit.CRH2A_ELECTRONIC_SIGNAGE)
                        .get().asItem().getDefaultInstance();
            }
            case LEGACY -> {
                next = plainPanel(state);
                if (next.equals(state)) return InteractionResult.PASS;
                if (state.getBlock() instanceof LacqueredBoardBlock)
                    recovered = EditablePanelItemInit.LACQUERED_BOARD.get().getDefaultInstance();
                else if (state.getBlock() instanceof TrainNoBlock)
                    recovered = EditablePanelItemInit.Sticker.get().getDefaultInstance();
            }
            case PANEL_TEXT -> {
                if (markings == null) return InteractionResult.PASS;
                recovered = LogoItemInit.PANEL_TEXT.get().getDefaultInstance();
            }
            case HARMONY -> {
                if (markings == null) return InteractionResult.PASS;
                recovered = LogoItemInit.HARMONY_LOGO.get().getDefaultInstance();
            }
            case MARKING_FIELDS -> {
                if (markings == null) return InteractionResult.PASS;
            }
            default -> { return InteractionResult.PASS; }
        }
        var previousCells = root == null ? Map.of(pos, state) : root.structure().partCells(level, pos, state);
        var nextCells = next.getBlock() instanceof PartStructure part
                ? part.partCells(level, pos, next) : Map.of(pos, next);
        boolean changed = PartInstallation.replace(level, previousCells, nextCells, at -> false,
                at -> level.mayInteract(player, at)
                        && player.mayUseItemAt(at, context.getClickedFace(), context.getItemInHand()),
                () -> {
                    switch (target.kind()) {
                        case NUMBER -> {
                            if (level.getBlockEntity(pos) instanceof PassengerDisplayHost host)
                                host.setCarriageNumber("");
                        }
                        case PANEL_TEXT -> markings.setPanelText(target.slot(), PanelText.EMPTY);
                        case HARMONY -> markings.setHarmony(target.slot(), 0);
                        case MARKING_FIELDS -> markings.update(new String[]{"", "", ""}, false);
                        default -> {}
                    }
                });
        if (!changed) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            if (!player.isCreative() && !recovered.isEmpty() && !player.addItem(recovered))
                player.drop(recovered, false);
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
        return PanelConversion.preservePlacement(state, plain);
    }
}
