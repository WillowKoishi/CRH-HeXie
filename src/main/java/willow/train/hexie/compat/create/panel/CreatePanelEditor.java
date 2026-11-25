package willow.train.hexie.compat.create.panel;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.ColoredBrushView;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.PanelTextItemView;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageNoSignEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageTypeSignEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.LacqueredBoardEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.TrainSpeedSignEntityView;
import willow.train.hexie.bridge.world.PartBlockBridge.PanelAttachmentHitKind;
import willow.train.hexie.bridge.world.PartBlockBridge.VehicleMarkingsView;

/** Opens existing editors on captured content without invoking placement or orientation gestures. */
public final class CreatePanelEditor {
    private CreatePanelEditor() {}

    public static InteractionHand editorHand(Player player, InteractionHand requested) {
        if (tool(player.getItemInHand(requested))) return requested;
        if (requested == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()) {
            if (tool(player.getOffhandItem())) return InteractionHand.OFF_HAND;
            if (player.isShiftKeyDown() && player.getOffhandItem().isEmpty()) return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    private static boolean tool(ItemStack stack) {
        return stack.getItem() instanceof ColoredBrushView || stack.getItem() instanceof PanelTextItemView;
    }

    public static boolean supports(BlockGetter view, BlockPos clicked) {
        var root = HexieBridges.partBlock().partStructures().resolve(view, clicked);
        var entity = view.getBlockEntity(root == null ? clicked : root.pos());
        return entity instanceof CarriageNoSignEntityView || entity instanceof CarriageTypeSignEntityView
                || entity instanceof LacqueredBoardEntityView || entity instanceof TrainSpeedSignEntityView
                || entity instanceof VehicleMarkingsView || entity instanceof CRH2HeadEntityView;
    }

    public static boolean open(ServerPlayer player, AbstractContraptionEntity carrier,
            InteractionHand requested, BlockHitResult hit) {
        if (!player.mayBuild() || player.isSpectator()) return false;
        var hand = editorHand(player, requested);
        if (hand == null) return false;
        var selected = CreatePanelInteractionTarget.validate(player, carrier, hit);
        if (selected == null) return false;
        var root = selected.rootPos();
        var entity = selected.view().getBlockEntity(root);
        CreatePanelEditTarget target;
        if (player.getItemInHand(hand).getItem() instanceof ColoredBrushView) {
            if (entity instanceof CRH2HeadEntityView) {
                target = CreatePanelEditTarget.createHeadNumber(player, carrier, root, hit.getBlockPos());
            } else {
                var kind = entity instanceof VehicleMarkingsView ? PanelAttachmentHitKind.MARKING_FIELDS : PanelAttachmentHitKind.LEGACY;
                target = CreatePanelEditTarget.create(player, carrier, root, hit.getBlockPos(), kind, 0);
            }
        } else {
            var attachment = selected.attachment();
            if (attachment.kind() != PanelAttachmentHitKind.PANEL_TEXT) return false;
            target = CreatePanelEditTarget.create(player, carrier, root, hit.getBlockPos(), attachment.kind(), attachment.slot());
        }
        if (target == null) return false;
        return switch (target.contentType()) {
            case CARRIAGE_NUMBER -> HexieBridges.menu().carriageNoSignEditMenu().openMoving(player, target);
            case CARRIAGE_TYPE -> HexieBridges.menu().carriageTypeSignEditMenu().openMoving(player, target);
            case LACQUERED -> HexieBridges.menu().lacqueredBoardEditMenu().openMoving(player, target);
            case SPEED -> HexieBridges.menu().trainSpeedSignEditMenu().openMoving(player, target);
            case MARKINGS -> HexieBridges.menu().carriageMarkingMenu().openMoving(player, target);
            case PANEL_TEXT -> HexieBridges.menu().panelTextMenu().openMoving(player, target, target.slot());
            case HEAD_NUMBER -> HexieBridges.menu().headNumberMenu().openMoving(player, target);
            case SS7D_NUMBER -> HexieBridges.menu().ss7DNumberMenu().openMoving(player, target);
        };
    }
}
