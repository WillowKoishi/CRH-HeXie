package willow.train.hexie.core.sticker;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/**
 * One persisted custom-sticker entry anchored at (anchorPos, facing).
 * Can span across multiple coplanar blocks or be offset entirely onto neighbor blocks.
 */
public record StickerEntry(
        UUID id,
        BlockPos anchorPos,
        Direction facing,
        StickerMode mode,
        WindowStickerParams window,
        DecalStickerParams decal,
        long revision
) {
    public record AnchorKey(BlockPos pos, Direction facing) {
        public AnchorKey {
            pos = pos.immutable();
            Objects.requireNonNull(facing);
        }
    }

    public StickerEntry {
        Objects.requireNonNull(id);
        anchorPos = anchorPos.immutable();
        Objects.requireNonNull(facing);
        Objects.requireNonNull(mode);
        window = window == null ? WindowStickerParams.DEFAULT : window;
        decal = decal == null ? DecalStickerParams.DEFAULT : decal;
    }

    public AnchorKey anchorKey() {
        return new AnchorKey(anchorPos, facing);
    }

    public StickerEntry withWindow(WindowStickerParams next) {
        return new StickerEntry(id, anchorPos, facing, StickerMode.WINDOW, next, decal, revision + 1);
    }

    public StickerEntry withDecal(DecalStickerParams next) {
        return new StickerEntry(id, anchorPos, facing, StickerMode.DECAL, window, next, revision + 1);
    }

    /**
     * Computes all coplanar block positions in the facing plane overlapped by this sticker's 2D footprint.
     * May exclude anchorPos if offset completely onto neighbor blocks!
     */
    public Set<BlockPos> affectedPositions() {
        float offU, offV, hw, hh;
        if (mode == StickerMode.WINDOW) {
            offU = window.offsetU();
            offV = window.offsetV();
            hw = window.width() * 0.5f;
            hh = window.height() * 0.5f;
        } else {
            offU = decal.offsetU();
            offV = decal.offsetV();
            double rad = Math.toRadians(decal.rotationDegrees());
            double c = Math.abs(Math.cos(rad)), s = Math.abs(Math.sin(rad));
            hw = (float) ((decal.width() * c + decal.height() * s) * 0.5);
            hh = (float) ((decal.width() * s + decal.height() * c) * 0.5);
        }
        var out = new LinkedHashSet<BlockPos>();
        Vec3 u = StickerPlaneMath.axisU(facing);
        Vec3 v = StickerPlaneMath.axisV(facing);
        Vec3 center = Vec3.atCenterOf(anchorPos);
        int uMin = (int) Math.floor(offU - hw + 0.5 - 1e-4);
        int uMax = (int) Math.floor(offU + hw + 0.5 - 1e-4);
        int vMin = (int) Math.floor(offV - hh + 0.5 - 1e-4);
        int vMax = (int) Math.floor(offV + hh + 0.5 - 1e-4);
        for (int du = uMin; du <= uMax; du++) {
            for (int dv = vMin; dv <= vMax; dv++) {
                Vec3 p = center.add(u.scale(du)).add(v.scale(dv));
                out.add(BlockPos.containing(p));
            }
        }
        return out;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putLong("Pos", anchorPos.asLong());
        tag.putString("Facing", facing.getName());
        tag.putString("Mode", mode.name());
        tag.put("Window", window.save());
        tag.put("Decal", decal.save());
        tag.putLong("Revision", revision);
        return tag;
    }

    public static StickerEntry load(CompoundTag tag) {
        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        BlockPos pos = BlockPos.of(tag.getLong("Pos"));
        Direction dir = Direction.byName(tag.getString("Facing"));
        if (dir == null) dir = Direction.NORTH;
        StickerMode mode = StickerMode.fromName(tag.getString("Mode"));
        return new StickerEntry(
                id, pos, dir, mode,
                WindowStickerParams.load(tag.getCompound("Window")),
                DecalStickerParams.load(tag.getCompound("Decal")),
                tag.getLong("Revision"));
    }

    public void writeBuf(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeBlockPos(anchorPos);
        buf.writeEnum(facing);
        buf.writeEnum(mode);
        window.writeBuf(buf);
        decal.writeBuf(buf);
        buf.writeVarLong(revision);
    }

    public static StickerEntry readBuf(FriendlyByteBuf buf) {
        return new StickerEntry(
                buf.readUUID(),
                buf.readBlockPos(),
                buf.readEnum(Direction.class),
                buf.readEnum(StickerMode.class),
                WindowStickerParams.readBuf(buf),
                DecalStickerParams.readBuf(buf),
                buf.readVarLong());
    }
}
