package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import willow.train.hexie.blockentity.*;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.blocks.parts.VehicleMarkings;
import willow.train.hexie.blocks.signs.PanelAttachmentHit;
import willow.train.hexie.menu.BlockEntityEditMenu;
import willow.train.hexie.mixins.mixin.ContraptionUpdateTagsAccessor;
import willow.train.hexie.network.s2c.PanelEditPacket;

/** A detached menu copy may change content; its placement and unrelated captured data never leave the server copy. */
public final class CreatePanelEditTarget implements BlockEntityEditMenu.Target {
    public enum ContentType { CARRIAGE_NUMBER, CARRIAGE_TYPE, LACQUERED, SPEED, MARKINGS, PANEL_TEXT, HEAD_NUMBER }

    private final ServerPlayer owner;
    private final AbstractContraptionEntity carrier;
    private final Contraption contraption;
    private final BlockPos rootPos, clickedPos;
    private final BlockState state;
    private final ContentType type;
    private final int slot;
    private final BlockEntity editor;
    private final CompoundTag originalContent, originalLayout;

    private CreatePanelEditTarget(ServerPlayer owner, AbstractContraptionEntity carrier, BlockPos rootPos,
            BlockPos clickedPos, ContentType type, int slot, BlockEntity editor) {
        this.owner = owner; this.carrier = carrier; this.contraption = carrier.getContraption();
        this.rootPos = rootPos.immutable(); this.clickedPos = clickedPos.immutable();
        this.state = contraption.getBlocks().get(rootPos).state(); this.type = type; this.slot = slot; this.editor = editor;
        var data = editor.saveWithFullMetadata(owner.registryAccess());
        originalContent = contents(type, slot, data);
        originalLayout = layout(type, slot, data);
    }

    public static CreatePanelEditTarget create(ServerPlayer player, AbstractContraptionEntity carrier,
            BlockPos rootPos, BlockPos clickedPos, PanelAttachmentHit.Kind kind, int slot) {
        var entity = capturedEntity(carrier, rootPos);
        if (entity == null) return null;
        ContentType type = switch (kind) {
            case LEGACY -> entity instanceof CarriageNoSignEntity ? ContentType.CARRIAGE_NUMBER
                    : entity instanceof CarriageTypeSignEntity ? ContentType.CARRIAGE_TYPE
                    : entity instanceof LacqueredBoardEntity ? ContentType.LACQUERED
                    : entity instanceof TrainSpeedSignEntity ? ContentType.SPEED : null;
            case MARKING_FIELDS -> entity instanceof VehicleMarkings ? ContentType.MARKINGS : null;
            case PANEL_TEXT -> entity instanceof VehicleMarkings markings
                    && entity.getBlockState().getBlock() instanceof willow.train.hexie.blocks.parts.VehicleMarkingSurface surface
                    && slot >= 0 && slot < surface.markingSlots(entity.getBlockState()) && slot < 2
                    && !markings.panelText(slot).text().isBlank() ? ContentType.PANEL_TEXT : null;
            default -> null;
        };
        return create(player, carrier, rootPos, clickedPos, type, type == ContentType.PANEL_TEXT ? slot : 0, entity);
    }

    public static CreatePanelEditTarget createHeadNumber(ServerPlayer player, AbstractContraptionEntity carrier,
            BlockPos rootPos, BlockPos clickedPos) {
        var entity = capturedEntity(carrier, rootPos);
        return create(player, carrier, rootPos, clickedPos,
                entity instanceof CRH2HeadEntity ? ContentType.HEAD_NUMBER : null, 0, entity);
    }

    private static CreatePanelEditTarget create(ServerPlayer player, AbstractContraptionEntity carrier,
            BlockPos rootPos, BlockPos clickedPos, ContentType type, int slot, BlockEntity editor) {
        if (type == null || editor == null) return null;
        var target = new CreatePanelEditTarget(player, carrier, rootPos, clickedPos, type, slot, editor);
        return target.originalContent != null && target.stillValid(player) ? target : null;
    }

    @Override public BlockEntity entity() { return editor; }
    public ContentType contentType() { return type; }
    public int slot() { return slot; }

    @Override public boolean stillValid(Player player) {
        if (player != owner || carrier.isRemoved() || carrier.level().isClientSide || carrier.level() != player.level()
                || carrier.getContraption() != contraption || player.isSpectator() || !player.mayBuild()
                || !carrier.canInteractWithBlock(player, clickedPos, 4)) return false;
        var info = contraption.getBlocks().get(rootPos);
        if (info == null || !info.state().equals(state)) return false;
        var view = new CreatePanelClipboardCopyPacket.CapturedView(carrier);
        var root = PartStructures.resolve(view, clickedPos);
        if (root == null && !rootPos.equals(clickedPos) || root != null && !root.pos().equals(rootPos)) return false;
        var cells = root == null ? Map.of(rootPos, state) : root.structure().partCells(view, rootPos, state);
        for (var entry : cells.entrySet()) {
            var captured = contraption.getBlocks().get(entry.getKey());
            if (captured == null || !captured.state().equals(entry.getValue())) return false;
            var position = carrier instanceof SnapshotCarriageEntity snapshot
                    ? CarriagePlayerSupport.frame(snapshot, 1).world(Vec3.atCenterOf(entry.getKey()))
                    : carrier.toGlobalVector(Vec3.atCenterOf(entry.getKey()), 1);
            var world = BlockPos.containing(position);
            if (!player.level().mayInteract(player, world)
                    || !player.mayUseItemAt(world, net.minecraft.core.Direction.UP, player.getMainHandItem())) return false;
        }
        var current = capturedEntity(carrier, rootPos);
        if (!matches(current, type)) return false;
        var data = current.saveWithFullMetadata(player.registryAccess());
        return originalContent.equals(contents(type, slot, data)) && originalLayout.equals(layout(type, slot, data));
    }

    @Override public boolean commit(Player player) {
        if (!stillValid(player)) return false;
        var content = contents(type, slot, editor.saveWithFullMetadata(player.registryAccess()));
        if (content == null) return false;
        if (content.equals(originalContent)) return true;
        // The state and geometry are unchanged: Create's generic setBlock sends a
        // structure-change packet and Snapshot also rebuilds every collision box.
        if (!updateCapturedContents(contraption, rootPos, type, slot, content, player.registryAccess())) return false;
        PacketDistributor.sendToPlayersTrackingEntity(carrier,
                new PanelEditPacket(carrier.getId(), carrier.getUUID(), rootPos, type, slot, content));
        return true;
    }

    /** Copy only known content keys into each of Create's three independently persisted representations. */
    public static boolean updateCapturedContents(Contraption contraption, BlockPos pos, ContentType type, int slot,
            CompoundTag payload, HolderLookup.Provider registries) {
        var previous = contraption.getBlocks().get(pos);
        if (previous == null || slot < 0 || slot > 1) return false;
        var current = loadEntity(previous, registries);
        if (!matches(current, type)) return false;
        var normalized = contents(type, slot, payload);
        if (normalized == null || !normalized.equals(payload)) return false;
        var data = previous.nbt() == null ? current.saveWithFullMetadata(registries) : previous.nbt().copy();
        writeContents(data, type, slot, normalized);
        var next = new StructureBlockInfo(pos, previous.state(), data);
        contraption.getBlocks().put(pos, next);
        var actor = IndexedContraptionActors.get(contraption, pos);
        if (actor != null) {
            actor.left = next;
            if (actor.right != null) {
                var actorData = data.copy();
                if (actor.right.blockEntityData != null) actorData.merge(actor.right.blockEntityData);
                writeContents(actorData, type, slot, normalized);
                actor.right.blockEntityData = actorData;
            }
        }
        var tags = ((ContraptionUpdateTagsAccessor) contraption).hexie$getUpdateTags();
        var update = data.copy();
        if (tags.containsKey(pos)) update.merge(tags.get(pos));
        writeContents(update, type, slot, normalized);
        tags.put(pos, update);
        return true;
    }

    private static BlockEntity capturedEntity(AbstractContraptionEntity carrier, BlockPos root) {
        var contraption = carrier.getContraption();
        return contraption == null ? null : loadEntity(contraption.getBlocks().get(root), carrier.registryAccess());
    }

    private static BlockEntity loadEntity(StructureBlockInfo info, HolderLookup.Provider registries) {
        if (info == null) return null;
        if (info.nbt() != null) return BlockEntity.loadStatic(info.pos(), info.state(), info.nbt().copy(), registries);
        return info.state().getBlock() instanceof EntityBlock factory ? factory.newBlockEntity(info.pos(), info.state()) : null;
    }

    private static boolean matches(BlockEntity entity, ContentType type) {
        return switch (type) {
            case CARRIAGE_NUMBER -> entity instanceof CarriageNoSignEntity;
            case CARRIAGE_TYPE -> entity instanceof CarriageTypeSignEntity;
            case LACQUERED -> entity instanceof LacqueredBoardEntity;
            case SPEED -> entity instanceof TrainSpeedSignEntity;
            case MARKINGS, PANEL_TEXT -> entity instanceof VehicleMarkings;
            case HEAD_NUMBER -> entity instanceof CRH2HeadEntity;
        };
    }

    /** Projection is also used to reject malformed or placement-bearing S2C data. */
    private static CompoundTag contents(ContentType type, int slot, CompoundTag data) {
        var result = new CompoundTag();
        switch (type) {
            case CARRIAGE_NUMBER -> { if (!text(data, result, "message", 384)) return null; integer(data, result, "color"); }
            case CARRIAGE_TYPE -> {
                for (var key : List.of("a", "b", "c", "d", "e")) if (!text(data, result, key, 384)) return null;
                integer(data, result, "Color");
            }
            case LACQUERED -> {
                for (int i = 0; i < 16; i++) if (!text(data, result, "content " + i, 384)) return null;
                for (var key : List.of("beltColor", "textColor", "pinyinColor")) integer(data, result, key);
            }
            case SPEED -> { if (!text(data, result, "content", 384)) return null; integer(data, result, "color"); }
            case MARKINGS -> { for (int i = 0; i < 3; i++) if (!text(data, result, "CarriageMarking" + i, 128)) return null; }
            case HEAD_NUMBER -> { if (!text(data, result, "HeadNumber", CRH2HeadEntity.MAX_LENGTH)) return null; }
            case PANEL_TEXT -> {
                if (slot < 0 || slot > 1 || !data.contains("PanelText" + slot, Tag.TAG_COMPOUND)) return null;
                var source = data.getCompound("PanelText" + slot); var label = new CompoundTag();
                if (!text(source, label, "Text", 128) || label.getString("Text").isBlank()
                        || !source.contains("FontSize", Tag.TAG_ANY_NUMERIC) || !Float.isFinite(source.getFloat("FontSize"))
                        || source.getFloat("FontSize") <= 0 || !source.contains("Font", Tag.TAG_STRING)
                        || source.getString("Font").length() > 128
                        || net.minecraft.resources.ResourceLocation.tryParse(source.getString("Font")) == null) return null;
                label.putFloat("FontSize", source.getFloat("FontSize")); label.putString("Font", source.getString("Font"));
                integer(source, label, "Spacing"); result.put("PanelText" + slot, label);
            }
        }
        return result;
    }

    private static boolean text(CompoundTag source, CompoundTag target, String key, int limit) {
        if (!source.contains(key, Tag.TAG_STRING)) return false;
        var value = source.getString(key);
        if (value.codePointCount(0, value.length()) > limit) return false;
        target.putString(key, value); return true;
    }
    private static void integer(CompoundTag source, CompoundTag target, String key) { target.putInt(key, source.getInt(key)); }

    private static CompoundTag layout(ContentType type, int slot, CompoundTag data) {
        var result = new CompoundTag();
        List<String> keys = switch (type) {
            case CARRIAGE_NUMBER -> List.of("left");
            case LACQUERED -> List.of("type", "x_offset");
            case SPEED -> List.of("x_offset", "y_offset", "x_revert", "y_revert");
            case MARKINGS -> List.of("DescriptionLeft", "CarriageMarkingSlot");
            default -> List.of();
        };
        for (var key : keys) if (data.contains(key)) result.put(key, data.get(key).copy());
        if (type == ContentType.PANEL_TEXT) {
            var label = data.getCompound("PanelText" + slot);
            for (var key : List.of("X", "Y")) if (label.contains(key)) result.put(key, label.get(key).copy());
        }
        return result;
    }

    private static void writeContents(CompoundTag target, ContentType type, int slot, CompoundTag content) {
        if (type == ContentType.PANEL_TEXT) {
            String key = "PanelText" + slot;
            var label = target.getCompound(key).copy();
            label.merge(content.getCompound(key)); target.put(key, label);
        } else for (var key : content.getAllKeys()) target.put(key, content.get(key).copy());
    }
}
