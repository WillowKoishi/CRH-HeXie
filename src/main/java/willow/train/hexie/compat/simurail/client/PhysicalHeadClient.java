package willow.train.hexie.compat.simurail.client;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import willow.train.hexie.compat.simurail.PhysicalHeadBogeyBlockEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import willow.train.hexie.compat.simurail.SimurailHeadRegistration;

/** Loaded only after the Simurail guard; the native controller keeps its shaft and connector rendering. */
public final class PhysicalHeadClient {
    private PhysicalHeadClient() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SimurailHeadRegistration.PHYSICAL_HEAD_BOGEY_ENTITY.get(), PhysicalHeadBogeyRenderer::new);
    }

    public static void registerVisuals() {
        SimpleBlockEntityVisualizer.builder(SimurailHeadRegistration.PHYSICAL_HEAD_BOGEY_ENTITY.get())
                .factory(PhysicalHeadBogeyVisual::new).apply();
    }

    public static int wheelLight(PhysicsBogeyBlockEntity entity, int fallback) {
        if (!(entity instanceof PhysicalHeadBogeyBlockEntity head) || entity.getLevel() == null) return fallback;
        var anchor = head.attachmentCenter();
        int light = LevelRenderer.getLightColor(entity.getLevel(), BlockPos.containing(anchor.x(), anchor.y(), anchor.z()));
        var subLevel = Sable.HELPER.getContainingClient(entity);
        return subLevel == null ? light : subLevel.scaleLightColor(light);
    }
}
