package willow.train.hexie.compat.sable;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge.SurfaceInputSourcesFrameView;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge.SurfaceInputSourcesQueryView;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge.SurfaceInputSourcesRayView;
import willow.train.hexie.core.api.interaction.WorldInteractionTargets;

/** Rendered plot coordinates are supplied from compatibility code, never selected by shared picking. */
public final class SableControlInputClient {
    private SableControlInputClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(SableControlInputClient::install));
    }

    private static void install() {
        HexieBridges.Client.surfaceInputClient().surfaceInputSources().registerSource((client, ray, partial, query) ->
                Plots.collect(client.level, ray, partial, query));
        HexieBridges.surfaceInput().surfaceInputSources().registerEye(SableInteractionCoordinates::renderEye);
        HexieBridges.surfaceInput().surfaceInputSources().registerFrame(target -> {
            if (target.carrier() != null || !(target.blocks() instanceof Level level)
                    || !SableInteractionCoordinates.contains(level, target.position())) return null;
            var frame = SableInteractionCoordinates.render(level, target.position(), target.partialTick());
            return new SurfaceInputSourcesFrameView() {
                @Override public Vec3 toWorld(Vec3 point) { return frame.toWorld(point); }
                @Override public SurfaceInputSourcesRayView toLocal(Vec3 from, Vec3 to) {
                    var ray = frame.ray(from, to);
                    return HexieBridges.surfaceInput().createSurfaceInputSourcesRay(ray.from(), ray.to());
                }
            };
        });
    }

    private static final class Plots {
        static void collect(Level level, SurfaceInputSourcesRayView ray, float partial, SurfaceInputSourcesQueryView query) {
            var bounds = new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                    new AABB(ray.from(), ray.to()).inflate(WorldInteractionTargets.OWNER_HALO));
            for (var plot : dev.ryanhcode.sable.Sable.HELPER.getAllIntersecting(level, bounds)) {
                if (!(plot instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel clientPlot)) continue;
                var pose = clientPlot.renderPose(partial);
                query.collectWorld(pose.transformPositionInverse(ray.from()), pose.transformPositionInverse(ray.to()));
            }
        }
    }
}
