package willow.train.hexie.compat.create.panel;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageNoSignEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.CarriageTypeSignEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.LacqueredBoardEntityView;
import willow.train.hexie.bridge.world.BlockEntityViews.TrainSpeedSignEntityView;
import willow.train.hexie.bridge.world.PartBlockBridge.PanelAttachmentHitKind;
import willow.train.hexie.bridge.world.PartBlockBridge.PanelAttachmentHitTargetView;
import willow.train.hexie.bridge.world.PartBlockBridge.VehicleMarkingSurfaceView;
import willow.train.hexie.bridge.world.PartBlockBridge.VehicleMarkingsView;
import willow.train.hexie.core.api.interaction.HexieClipboardCloneable;

/** Create's clipboard protocol, scoped to the visible attachment instead of the whole panel. */
public final class CreatePanelClipboard {
    public static final String MARKING_KEY = "hexie_carriage_marking";
    public static final String TEXT_KEY = "hexie_panel_text";
    public static final String CRH_LOGO_KEY = "hexie_crh_logo";

    private CreatePanelClipboard() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        var hand = clipboardHand(event.getEntity(), event.getHand());
        if (hand == null || event.getEntity().isShiftKeyDown()) return;
        if (!panel(event.getLevel(), event.getPos())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        var player = event.getEntity();
        if (player.isSpectator() || event.getLevel() != player.level()) return;
        var target = HexieBridges.partBlock().panelAttachmentHit().resolve(event.getLevel(), event.getPos(), event.getHitVec());
        if (!event.getLevel().mayInteract(player, target.pos()) || event.getLevel().isClientSide()) return;
        if (copy(event.getLevel(), target, player.getItemInHand(hand), event.getLevel().registryAccess(), event.getFace()))
            feedback(player, target, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!AllBlocks.CLIPBOARD.isIn(event.getItemStack()) || event.getEntity().isShiftKeyDown()) return;
        if (!panel(event.getLevel(), event.getPos())) return;
        // Also consume an invalid paste: the clipboard must never break the host or fall
        // through to Create's whole-BE handler after missing this attachment's bounds.
        event.setCanceled(true);
        var player = event.getEntity();
        var picked = player.pick(player.blockInteractionRange(), 1, false);
        if (!(picked instanceof BlockHitResult hit) || !hit.getBlockPos().equals(event.getPos())) return;
        var target = HexieBridges.partBlock().panelAttachmentHit().resolve(event.getLevel(), event.getPos(), hit);
        if (target.kind() == PanelAttachmentHitKind.NONE) {
            var values = event.getItemStack().getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY)
                    .copiedValues().orElse(null);
            if (values != null && values.contains(MARKING_KEY, Tag.TAG_COMPOUND))
                target = HexieBridges.partBlock().panelAttachmentHit().emptyMarkingTarget(event.getLevel(), event.getPos(), hit);
        }
        if (paste(event.getLevel(), target, event.getItemStack(), player, event.getFace(), event.getLevel().isClientSide())
                && !event.getLevel().isClientSide()) feedback(player, target, true);
    }

    public static InteractionHand clipboardHand(Player player, InteractionHand hand) {
        if (AllBlocks.CLIPBOARD.isIn(player.getItemInHand(hand))) return hand;
        return hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                && AllBlocks.CLIPBOARD.isIn(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
    }

    public static boolean panel(BlockGetter world, net.minecraft.core.BlockPos clicked) {
        var root = HexieBridges.partBlock().partStructures().resolve(world, clicked);
        var pos = root == null ? clicked : root.pos();
        var block = world.getBlockState(pos).getBlock();
        return HexieBridges.signBlock().passengerDisplayPanel().supports(block) || block instanceof VehicleMarkingSurfaceView || legacy(world.getBlockEntity(pos));
    }

    /** Copies values only; clipboard pages, checklist state and read-only flag stay intact. */
    public static boolean copy(BlockGetter world, PanelAttachmentHitTargetView target, ItemStack stack,
            HolderLookup.Provider registries, Direction side) {
        if (!AllBlocks.CLIPBOARD.isIn(stack)) return false;
        var cloneable = attachment(world, target);
        if (cloneable == null) return false;
        var value = new CompoundTag();
        if (!cloneable.writeToClipboard(registries, value, side)) return false;
        var values = new CompoundTag();
        values.put(cloneable.getClipboardKey(), value);
        var content = stack.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
        stack.set(AllDataComponents.CLIPBOARD_CONTENT, content.setType(ClipboardType.WRITTEN).setCopiedValues(values));
        return true;
    }

    public static boolean paste(Level world, PanelAttachmentHitTargetView target, ItemStack stack,
            Player player, Direction side, boolean simulate) {
        if (target == null || player == null || world != player.level() || !AllBlocks.CLIPBOARD.isIn(stack)) return false;
        var be = world.getBlockEntity(target.pos());
        if (!canPaste(be, player, side)) return false;
        var cloneable = attachment(world, target, true);
        if (cloneable == null) return false;
        var content = stack.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
        var values = content.copiedValues().orElse(null);
        return values != null && values.contains(cloneable.getClipboardKey(), Tag.TAG_COMPOUND)
                && cloneable.readFromClipboard(world.registryAccess(), values.getCompound(cloneable.getClipboardKey()),
                player, side, simulate || world.isClientSide());
    }

    /** Guard the legacy native ClipboardCloneable entry points as well as our targeted adapter. */
    public static boolean canPaste(BlockEntity entity, Player player, Direction side) {
        if (entity == null || player == null || entity.isRemoved() || player.isSpectator() || !player.mayBuild()) return false;
        var level = entity.getLevel();
        var pos = entity.getBlockPos();
        if (level == null || level != player.level() || !level.hasChunkAt(pos)
                || level.getBlockEntity(pos) != entity || !level.getBlockState(pos).equals(entity.getBlockState())) return false;
        var root = HexieBridges.partBlock().partStructures().resolve(level, pos);
        var cells = root == null ? java.util.Set.of(pos) : root.structure().partCells(level, root.pos(), root.state()).keySet();
        boolean inReach = false;
        for (var cell : cells) {
            if (!level.hasChunkAt(cell) || !level.mayInteract(player, cell)
                    || !player.mayUseItemAt(cell, side == null ? Direction.UP : side, player.getMainHandItem())) return false;
            inReach |= player.canInteractWithBlock(cell, 1);
        }
        return inReach;
    }

    private static boolean legacy(BlockEntity entity) {
        return entity instanceof CarriageNoSignEntityView || entity instanceof CarriageTypeSignEntityView
                || entity instanceof LacqueredBoardEntityView || entity instanceof TrainSpeedSignEntityView;
    }

    /** The adapter captures a target slot, never a mutable "last selected slot" on the BE. */
    public static ClipboardCloneable attachment(BlockGetter world, PanelAttachmentHitTargetView target) {
        return attachment(world, target, false);
    }

    private static ClipboardCloneable attachment(BlockGetter world, PanelAttachmentHitTargetView target, boolean allowEmptyFields) {
        if (target == null || !world.getBlockState(target.pos()).equals(target.state())) return null;
        var entity = world.getBlockEntity(target.pos());
        if (target.kind() == PanelAttachmentHitKind.LEGACY)
            return legacy(entity) && entity instanceof HexieClipboardCloneable sign ? new SignClipboard(sign) : null;
        if (!(entity instanceof VehicleMarkingsView markings)
                || !(target.state().getBlock() instanceof VehicleMarkingSurfaceView surface)
                || target.slot() < 0 || target.slot() >= surface.markingSlots(target.state())) return null;
        boolean present = switch (target.kind()) {
            case MARKING_FIELDS -> markings.markingSlot() == target.slot()
                    && java.util.Arrays.stream(markings.fields()).anyMatch(text -> !text.isBlank())
                    || allowEmptyFields && java.util.Arrays.stream(markings.fields()).allMatch(String::isBlank)
                    && markings.harmony(target.slot()) == 0 && markings.panelText(target.slot()).text().isEmpty();
            case PANEL_TEXT -> !markings.panelText(target.slot()).text().isEmpty();
            case HARMONY -> markings.harmony(target.slot()) != 0;
            default -> false;
        };
        return present ? new MarkingClipboard(entity, markings, target) : null;
    }

    public static void feedback(Player player, PanelAttachmentHitTargetView target, boolean paste) {
        player.displayClientMessage(CreateLang.translate(paste ? "clipboard.pasted_to" : "clipboard.copied_from",
                target.state().getBlock().getName().withStyle(ChatFormatting.WHITE))
                .style(ChatFormatting.GREEN).component(), true);
    }

    private record SignClipboard(HexieClipboardCloneable sign) implements ClipboardCloneable {
        @Override public String getClipboardKey() {
            return sign.getClipboardKey();
        }

        @Override public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
            return sign.writeToClipboard(registries, tag, side);
        }

        @Override public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player,
                Direction side, boolean simulate) {
            return sign.readFromClipboard(registries, tag, player, side, simulate);
        }
    }

    private record MarkingClipboard(BlockEntity entity, VehicleMarkingsView markings,
            PanelAttachmentHitTargetView target) implements ClipboardCloneable {
        @Override public String getClipboardKey() {
            return switch (target.kind()) {
                case MARKING_FIELDS -> MARKING_KEY;
                case PANEL_TEXT -> TEXT_KEY;
                case HARMONY -> CRH_LOGO_KEY;
                default -> throw new IllegalStateException("Not a marking attachment");
            };
        }

        @Override public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
            tag.putString("Kind", getClipboardKey());
            switch (target.kind()) {
                case MARKING_FIELDS -> {
                    var fields = markings.fields();
                    for (int i = 0; i < fields.length; i++) tag.putString("Field" + i, fields[i]);
                    tag.putBoolean("DescriptionLeft", markings.descriptionLeft());
                }
                case PANEL_TEXT -> tag.put("Text", markings.panelText(target.slot()).save());
                case HARMONY -> tag.putInt("Orientation", markings.harmony(target.slot()));
                default -> { return false; }
            }
            return true;
        }

        @Override public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag,
                Player player, Direction side, boolean simulate) {
            if (!canPaste(entity, player, side) || !tag.getString("Kind").equals(getClipboardKey())
                    || attachment(entity.getLevel(), target, true) == null) return false;
            switch (target.kind()) {
                case MARKING_FIELDS -> {
                    var fields = new String[3];
                    for (int i = 0; i < fields.length; i++) {
                        if (!tag.contains("Field" + i, Tag.TAG_STRING)) return false;
                        fields[i] = HexieBridges.worldBlock().groundStationSignEntity().clean(tag.getString("Field" + i));
                    }
                    if (!tag.contains("DescriptionLeft", Tag.TAG_BYTE)
                            || java.util.Arrays.stream(fields).allMatch(String::isBlank)) return false;
                    if (!simulate) {
                        markings.selectMarkingSlot(target.slot());
                        markings.update(fields, tag.getBoolean("DescriptionLeft"));
                    }
                }
                case PANEL_TEXT -> {
                    if (!tag.contains("Text", Tag.TAG_COMPOUND)) return false;
                    var value = HexieBridges.partBlock().panelText().load(tag.getCompound("Text"));
                    if (value.text().isBlank()) return false;
                    if (!simulate) markings.setPanelText(target.slot(), value);
                }
                case HARMONY -> {
                    int orientation = tag.getInt("Orientation");
                    if (orientation != 1 && orientation != 2) return false;
                    if (!simulate) markings.setHarmony(target.slot(), orientation);
                }
                default -> { return false; }
            }
            return true;
        }
    }
}
