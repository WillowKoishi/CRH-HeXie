package willow.train.hexie.render.coupler;


// split from willow/train/hexie/client/render/SnapshotCouplerRenderer.java, lines 70-108

import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotBogeyAnimationFrameView;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.SnapshotCouplerAnimationAdapterAttachmentView;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;

public final class CouplerAdapterBounds {
    /** Adapter stacks may extend past Create's captured structure envelope. */
    public static net.minecraft.world.phys.AABB adapterBounds(SnapshotCarriageView entity, float partialTick) {
        var animation = entity.couplerAnimation();
        var body = HexieBridges.createPlayer().frame(entity, partialTick);
        net.minecraft.world.phys.AABB result = null;
        for (int i = 0; i < animation.frames().size() && i < animation.parameters().size(); i++) {
            var attachment = animation.parameters(i, partialTick).attachment();
            if (attachment == null) continue;
            var bounds = adapterBounds(attachment, animation.frame(i, partialTick), body::world);
            if (bounds != null) result = result == null ? bounds : result.minmax(bounds);
        }
        return result;
    }

    /** Shares the arm/contact transforms used below, with the full knuckle sweep included. */
    public static net.minecraft.world.phys.AABB adapterBounds(SnapshotCouplerAnimationAdapterAttachmentView attachment,
            SnapshotBogeyAnimationFrameView frame,
            java.util.function.UnaryOperator<net.minecraft.world.phys.Vec3> localToWorld) {
        var adapter = attachment.adapter();
        String baseModel = attachment.reversed() ? adapter.secondModel() : adapter.firstModel();
        var box = HexieBridges.worldBlock().couplerAdapterGeometry().bounds(adapter, baseModel);
        if (box == null) return null;
        var contact = attachment.complete();
        var pivot = attachment.pivot();
        box = box.move(contact.x() - pivot.x(), contact.y() - pivot.y(), contact.z() - pivot.z());
        var rotation = frame.rotation();
        net.minecraft.world.phys.AABB result = null;
        for (int corner = 0; corner < 8; corner++) {
            var point = new org.joml.Vector3f((float) ((corner & 1) == 0 ? box.minX : box.maxX),
                    (float) ((corner & 2) == 0 ? box.minY : box.maxY),
                    (float) ((corner & 4) == 0 ? box.minZ : box.maxZ));
            rotation.transform(point);
            var world = localToWorld.apply(new net.minecraft.world.phys.Vec3(
                    point.x + frame.x(), point.y + frame.y(), point.z + frame.z()));
            var single = new net.minecraft.world.phys.AABB(world, world);
            result = result == null ? single : result.minmax(single);
        }
        return result;
    }
}
