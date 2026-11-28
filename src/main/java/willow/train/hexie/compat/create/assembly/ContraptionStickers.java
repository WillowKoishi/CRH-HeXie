package willow.train.hexie.compat.create.assembly;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.sticker.ContraptionStickerHost;
import willow.train.hexie.core.sticker.DecalStickerParams;
import willow.train.hexie.core.sticker.StickerEntry;
import willow.train.hexie.core.sticker.StickerMode;
import willow.train.hexie.core.sticker.StickerPlaneMath;
import willow.train.hexie.core.sticker.WindowStickerParams;

/**
 * Transfers chunk-stored custom stickers (windows & decals) onto Create Contraptions at assembly,
 * serializes them across save/reload and client sync, and restores them to world chunks on disassembly.
 */
public final class ContraptionStickers {
    public static final String NBT_KEY = "HexieStickers";

    public record Capture(List<StickerEntry> localEntries, List<StickerEntry> worldEntries) {
        public static final Capture EMPTY = new Capture(List.of(), List.of());
        public boolean isEmpty() { return localEntries.isEmpty(); }
    }

    private ContraptionStickers() {}

    /** Finds all world stickers affecting any assembled block cell and converts them to contraption-local coords. */
    public static Capture captureFromWorld(ServerLevel level, BlockPos origin, Collection<BlockPos> localPositions) {
        if (level == null || origin == null || localPositions == null || localPositions.isEmpty()) {
            return Capture.EMPTY;
        }
        var seen = new LinkedHashMap<UUID, StickerEntry>();
        for (BlockPos localPos : localPositions) {
            BlockPos worldPos = origin.offset(localPos);
            for (StickerEntry e : HexieBridges.sticker().entriesAffecting(level, worldPos)) {
                seen.putIfAbsent(e.id(), e);
            }
        }
        if (seen.isEmpty()) return Capture.EMPTY;
        var local = new ArrayList<StickerEntry>(seen.size());
        var world = new ArrayList<StickerEntry>(seen.size());
        for (StickerEntry e : seen.values()) {
            world.add(e);
            BlockPos localAnchor = e.anchorPos().subtract(origin).immutable();
            local.add(new StickerEntry(e.id(), localAnchor, e.facing(), e.mode(), e.window(), e.decal(), e.revision()));
        }
        return new Capture(List.copyOf(local), List.copyOf(world));
    }

    public static void removeWorldStickers(ServerLevel level, List<StickerEntry> worldEntries) {
        if (level == null || worldEntries == null || worldEntries.isEmpty()) return;
        for (StickerEntry e : worldEntries) {
            HexieBridges.sticker().removeAtServer(level, e.anchorPos(), e.facing());
        }
    }

    /** Removes world stickers matching a contraption's local stickers relative to its original assembly origin. */
    public static void removeCapturedFromWorld(ServerLevel level, BlockPos origin, Contraption contraption) {
        if (level == null || origin == null || !(contraption instanceof ContraptionStickerHost host)) return;
        var entries = host.hexie$stickers();
        if (entries.isEmpty()) return;
        for (StickerEntry e : entries) {
            HexieBridges.sticker().removeAtServer(level, origin.offset(e.anchorPos()), e.facing());
        }
    }

    /** Transforms contraption-local stickers back into world space via Create's StructureTransform and upserts them. */
    public static void restoreToWorld(ServerLevel level, StructureTransform transform, List<StickerEntry> localEntries) {
        if (level == null || transform == null || localEntries == null || localEntries.isEmpty()) return;
        for (StickerEntry e : localEntries) {
            StickerEntry restored = transformEntry(e, transform);
            HexieBridges.sticker().upsertServer(level, restored);
        }
    }

    private static StickerEntry transformEntry(StickerEntry e, StructureTransform transform) {
        BlockPos worldAnchor = transform.apply(e.anchorPos()).immutable();
        Direction oldFacing = e.facing();
        Direction newFacing = transform.rotateFacing(oldFacing);

        Vec3 oldU = StickerPlaneMath.axisU(oldFacing);
        Vec3 oldV = StickerPlaneMath.axisV(oldFacing);
        Vec3 tU = transform.applyWithoutOffsetUncentered(oldU);
        Vec3 tV = transform.applyWithoutOffsetUncentered(oldV);
        Vec3 newU = StickerPlaneMath.axisU(newFacing);
        Vec3 newV = StickerPlaneMath.axisV(newFacing);

        double uDotNewU = tU.dot(newU);
        double uDotNewV = tU.dot(newV);
        boolean swappedAxes = Math.abs(uDotNewV) > 0.5;

        if (e.mode() == StickerMode.WINDOW) {
            var w = e.window();
            Vec3 disp = tU.scale(w.offsetU()).add(tV.scale(w.offsetV()));
            float offU = (float) disp.dot(newU);
            float offV = (float) disp.dot(newV);
            float width = swappedAxes ? w.height() : w.width();
            float height = swappedAxes ? w.width() : w.height();
            var nextWin = new WindowStickerParams(
                    w.frameColorRgb(), w.glassColorRgb(), w.glassAlpha(),
                    w.frameThickness(), w.cornerRadius(), width, height, offU, offV);
            return new StickerEntry(e.id(), worldAnchor, newFacing, StickerMode.WINDOW, nextWin, e.decal(), e.revision() + 1L);
        } else {
            var d = e.decal();
            Vec3 disp = tU.scale(d.offsetU()).add(tV.scale(d.offsetV()));
            float offU = (float) disp.dot(newU);
            float offV = (float) disp.dot(newV);
            double extraDeg = Math.toDegrees(Math.atan2(uDotNewV, uDotNewU));
            float rot = (float) (((d.rotationDegrees() + extraDeg) % 360.0 + 360.0) % 360.0);
            var nextDecal = new DecalStickerParams(d.imageHash(), d.width(), d.height(), rot, offU, offV);
            return new StickerEntry(e.id(), worldAnchor, newFacing, StickerMode.DECAL, e.window(), nextDecal, e.revision() + 1L);
        }
    }

    public static ListTag writeListTag(List<StickerEntry> entries) {
        var list = new ListTag();
        for (StickerEntry e : entries) {
            list.add(e.save());
        }
        return list;
    }

    public static List<StickerEntry> readListTag(CompoundTag parent, String key) {
        if (parent == null || !parent.contains(key, Tag.TAG_LIST)) return List.of();
        ListTag list = parent.getList(key, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return List.of();
        var out = new ArrayList<StickerEntry>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(StickerEntry.load(list.getCompound(i)));
        }
        return List.copyOf(out);
    }
}
