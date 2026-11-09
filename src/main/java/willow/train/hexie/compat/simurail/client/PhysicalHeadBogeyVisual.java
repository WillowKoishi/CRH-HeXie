package willow.train.hexie.compat.simurail.client;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.crystaelix.simurail.content.bogey.PhysicsBogeyVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import willow.train.hexie.compat.simurail.PhysicalHeadBogeyBlockEntity;

/** The visual spans the rear control and the independent wheel mount, including light updates across sections. */
public final class PhysicalHeadBogeyVisual extends PhysicsBogeyVisual {
    private Vec3 lastAttachmentOffset;

    public PhysicalHeadBogeyVisual(VisualizationContext context, PhysicsBogeyBlockEntity entity, float partialTick) {
        super(context, entity, partialTick);
        lightControlShaft();
    }

    @Override public boolean isVisible(FrustumIntersection frustum) {
        var bounds = blockEntity.getRenderBoundingBox();
        double dx = visualPos.getX() - (double) pos.getX();
        double dy = visualPos.getY() - (double) pos.getY();
        double dz = visualPos.getZ() - (double) pos.getZ();
        return frustum.testAab((float) (bounds.minX + dx), (float) (bounds.minY + dy), (float) (bounds.minZ + dz),
                (float) (bounds.maxX + dx), (float) (bounds.maxY + dy), (float) (bounds.maxZ + dz));
    }

    @Override public boolean doDistanceLimitThisFrame(DynamicVisual.Context context) {
        if (!(blockEntity instanceof PhysicalHeadBogeyBlockEntity head)) return super.doDistanceLimitThisFrame(context);
        var camera = context.camera().getPosition();
        var anchor = head.attachmentCenter();
        double distance = Math.min(pos.distToCenterSqr(camera), camera.distanceToSqr(anchor.x(), anchor.y(), anchor.z()));
        return !context.limiter().shouldUpdate(distance);
    }

    @Override public void setSectionCollector(SectionTrackedVisual.SectionCollector collector) {
        lightSections = collector;
        collectLightSections();
    }

    @Override public void beginFrame(DynamicVisual.Context context) {
        super.beginFrame(context);
        if (blockEntity instanceof PhysicalHeadBogeyBlockEntity head
                && !head.attachmentOffset().equals(lastAttachmentOffset)) {
            collectLightSections();
            updateLight(context.partialTick());
        }
    }

    @Override protected int computePackedLight() {
        return PhysicalHeadClient.wheelLight(blockEntity, super.computePackedLight());
    }

    @Override public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        lightControlShaft();
    }

    private void lightControlShaft() {
        if (!blockEntity.isUnpowered()) rotatingModel.light(super.computePackedLight()).setChanged();
    }

    private void collectLightSections() {
        if (!(blockEntity instanceof PhysicalHeadBogeyBlockEntity head)) return;
        lastAttachmentOffset = head.attachmentOffset();
        if (lightSections == null) return;
        var anchor = head.attachmentCenter();
        var bounds = new AABB(pos).minmax(new AABB(anchor.x() - 3, anchor.y() - 3, anchor.z() - 3,
                anchor.x() + 3, anchor.y() + 3, anchor.z() + 3));
        var min = SectionPos.of(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ));
        var max = SectionPos.of(BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ));
        var sections = new LongOpenHashSet();
        for (int x = min.x(); x <= max.x(); x++) for (int y = min.y(); y <= max.y(); y++)
            for (int z = min.z(); z <= max.z(); z++) sections.add(SectionPos.asLong(x, y, z));
        lightSections.sections(sections);
    }
}
