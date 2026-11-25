package willow.train.hexie.compat.create.motion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.vehicle.Point3;

// split from SnapshotCouplerAnimation.java
public final class SnapshotCouplerAnimationNbt {
    private final SnapshotCouplerAnimation animationState;

    public SnapshotCouplerAnimationNbt(SnapshotCouplerAnimation animationState) { this.animationState = animationState; }

    public CompoundTag write() {
        CompoundTag root = animationState.poses.write();
        if (animationState.floorOffset != 0) root.putDouble("FloorOffset", animationState.floorOffset);
        var hooks = new ListTag();
        for (var parameters : animationState.currentParameters) {
            var tag = new CompoundTag();
            tag.putUUID("Id", parameters.endpoint());
            tag.putLong("Pos", parameters.position().asLong());
            tag.putDouble("Extension", parameters.extension());
            tag.putDouble("Yaw", parameters.angles().yaw());
            tag.putDouble("Pitch", parameters.angles().pitch());
            tag.putBoolean("Coupled", parameters.coupled());
            if (parameters.visual() != null) tag.put("Visual", HexieBridges.vehicle().vehicleRuntimeNbt().writeCouplerVisual(parameters.visual()));
            if (parameters.partnerPose() != null) {
                var partner = parameters.partnerPose();
                var partnerTag = new CompoundTag();
                partnerTag.putUUID("Id", partner.endpoint());
                writePoint(partnerTag, "Origin", partner.origin());
                partnerTag.putFloat("Qx", partner.qx()); partnerTag.putFloat("Qy", partner.qy());
                partnerTag.putFloat("Qz", partner.qz()); partnerTag.putFloat("Qw", partner.qw());
                if (partner.state() != null) partnerTag.put("State", HexieBridges.vehicle().vehicleRuntimeNbt().renderState(partner.state()));
                tag.put("PartnerPose", partnerTag);
            }
            if (parameters.attachment() != null) {
                var attachment = parameters.attachment();
                var adapter = new CompoundTag();
                HexieBridges.couplerBlock().couplerAdapterData().write(adapter, attachment.adapter());
                adapter.putBoolean("Reversed", attachment.reversed());
                writePoint(adapter, "Pivot", attachment.pivot());
                writePoint(adapter, "Complete", attachment.complete());
                tag.put("Adapter", adapter);
            }
            hooks.add(tag);
        }
        root.put("Hooks", hooks);
        return root;
    }

    public void read(CompoundTag root, int steps) {
        animationState.poses.read(root, steps);
        animationState.floorOffset = root.getDouble("FloorOffset");
        animationState.targetParameters = decodeParameters(root);
        boolean sameHooks = animationState.currentParameters.size() == animationState.targetParameters.size();
        for (int i = 0; sameHooks && i < animationState.currentParameters.size(); i++) {
            var current = animationState.currentParameters.get(i);
            var target = animationState.targetParameters.get(i);
            sameHooks = current.endpoint().equals(target.endpoint()) && current.position().equals(target.position())
                    && Objects.equals(current.visual(), target.visual());
        }
        // Legacy animation payloads still supply poses; their absent hook parameters stay empty.
        if (!sameHooks || steps <= 0) animationState.previousParameters = animationState.currentParameters = animationState.targetParameters;
        animationState.parameterInterpolationSteps = Math.max(1, steps);
    }

    public static SnapshotCouplerAnimation.State decode(CompoundTag root) {
        return new SnapshotCouplerAnimation.State(SnapshotBogeyAnimation.decode(root), decodeParameters(root), root.getDouble("FloorOffset"));
    }

    public static List<SnapshotCouplerAnimation.Parameters> decodeParameters(CompoundTag root) {
        var decoded = new ArrayList<SnapshotCouplerAnimation.Parameters>();
        for (Tag value : root.getList("Hooks", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) value;
            if (!tag.hasUUID("Id") || !tag.contains("Pos", Tag.TAG_LONG)
                    || !tag.contains("Extension", Tag.TAG_DOUBLE) || !tag.contains("Yaw", Tag.TAG_DOUBLE)
                    || !tag.contains("Pitch", Tag.TAG_DOUBLE)) continue;
            SnapshotCouplerAnimation.AdapterAttachment attachment = null;
            if (tag.contains("Adapter", Tag.TAG_COMPOUND)) {
                var adapter = tag.getCompound("Adapter");
                var definition = HexieBridges.couplerBlock().couplerAdapterData().read(adapter);
                if (definition != null) attachment = new SnapshotCouplerAnimation.AdapterAttachment(definition,
                        readPoint(adapter, "Pivot"), readPoint(adapter, "Complete"), adapter.getBoolean("Reversed"));
            }
            SnapshotCouplerAnimation.PartnerPose partner = null;
            if (tag.contains("PartnerPose", Tag.TAG_COMPOUND)) {
                var partnerTag = tag.getCompound("PartnerPose");
                if (partnerTag.hasUUID("Id") && partnerTag.contains("Origin", Tag.TAG_COMPOUND)
                        && partnerTag.contains("Qx", Tag.TAG_FLOAT) && partnerTag.contains("Qy", Tag.TAG_FLOAT)
                        && partnerTag.contains("Qz", Tag.TAG_FLOAT) && partnerTag.contains("Qw", Tag.TAG_FLOAT))
                    partner = new SnapshotCouplerAnimation.PartnerPose(partnerTag.getUUID("Id"), readPoint(partnerTag, "Origin"),
                            partnerTag.getFloat("Qx"), partnerTag.getFloat("Qy"),
                            partnerTag.getFloat("Qz"), partnerTag.getFloat("Qw"),
                            partnerTag.contains("State", Tag.TAG_COMPOUND)
                                    ? HexieBridges.vehicle().vehicleRuntimeNbt().renderState(partnerTag, "State") : null);
            }
            decoded.add(new SnapshotCouplerAnimation.Parameters(tag.getUUID("Id"), BlockPos.of(tag.getLong("Pos")), tag.getDouble("Extension"),
                    HexieBridges.coupling().couplerAngles().create(tag.getDouble("Yaw"), tag.getDouble("Pitch")), attachment,
                    !tag.contains("Coupled", Tag.TAG_BYTE) || tag.getBoolean("Coupled"), partner,
                    tag.contains("Visual") ? HexieBridges.vehicle().vehicleRuntimeNbt().readCouplerVisual(tag.getCompound("Visual")) : null));
        }
        return List.copyOf(decoded);
    }

    public static void writePoint(CompoundTag root, String key, Point3 point) {
        var tag = new CompoundTag();
        tag.putDouble("X", point.x()); tag.putDouble("Y", point.y()); tag.putDouble("Z", point.z());
        root.put(key, tag);
    }

    public static Point3 readPoint(CompoundTag root, String key) {
        var tag = root.getCompound(key);
        return new Point3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
    }
}
