package willow.train.hexie.compat.simurail.client;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.compat.simurail.PhysicalHeadBogeyBlockEntity;

/** A camera near the wheel mount remains in range even when the rear controller is farther away. */
public final class PhysicalHeadBogeyRenderer extends PhysicsBogeyRenderer {
    public PhysicalHeadBogeyRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @Override public boolean shouldRender(PhysicsBogeyBlockEntity entity, Vec3 camera) {
        if (super.shouldRender(entity, camera)) return true;
        if (!(entity instanceof PhysicalHeadBogeyBlockEntity head)) return false;
        var anchor = head.attachmentCenter();
        double distance = getViewDistance();
        return camera.distanceToSqr(anchor.x(), anchor.y(), anchor.z()) < distance * distance;
    }
}
