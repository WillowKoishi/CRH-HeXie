package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.blockentity.*;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.blocks.parts.VehicleMarkings;
import willow.train.hexie.blocks.signs.PanelAttachmentHit;
import willow.train.hexie.item.PanelTextItem;
import willow.train.hexie.item.editable_panels.ColoredBrush;
import willow.train.hexie.menu.*;

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
        return stack.getItem() instanceof ColoredBrush || stack.getItem() instanceof PanelTextItem;
    }

    public static boolean supports(BlockGetter view, BlockPos clicked) {
        var root = PartStructures.resolve(view, clicked);
        var entity = view.getBlockEntity(root == null ? clicked : root.pos());
        return entity instanceof CarriageNoSignEntity || entity instanceof CarriageTypeSignEntity
                || entity instanceof LacqueredBoardEntity || entity instanceof TrainSpeedSignEntity
                || entity instanceof VehicleMarkings || entity instanceof CRH2HeadEntity;
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
        if (player.getItemInHand(hand).getItem() instanceof ColoredBrush) {
            if (entity instanceof CRH2HeadEntity) {
                target = CreatePanelEditTarget.createHeadNumber(player, carrier, root, hit.getBlockPos());
            } else {
                var kind = entity instanceof VehicleMarkings ? PanelAttachmentHit.Kind.MARKING_FIELDS : PanelAttachmentHit.Kind.LEGACY;
                target = CreatePanelEditTarget.create(player, carrier, root, hit.getBlockPos(), kind, 0);
            }
        } else {
            var attachment = selected.attachment();
            if (attachment.kind() != PanelAttachmentHit.Kind.PANEL_TEXT) return false;
            target = CreatePanelEditTarget.create(player, carrier, root, hit.getBlockPos(), attachment.kind(), attachment.slot());
        }
        if (target == null) return false;
        return switch (target.contentType()) {
            case CARRIAGE_NUMBER -> CarriageNoSignEditMenu.openMoving(player, target);
            case CARRIAGE_TYPE -> CarriageTypeSignEditMenu.openMoving(player, target);
            case LACQUERED -> LacqueredBoardEditMenu.openMoving(player, target);
            case SPEED -> TrainSpeedSignEditMenu.openMoving(player, target);
            case MARKINGS -> CarriageMarkingMenu.openMoving(player, target);
            case PANEL_TEXT -> PanelTextMenu.openMoving(player, target, target.slot());
            case HEAD_NUMBER -> HeadNumberMenu.openMoving(player, target);
        };
    }
}
