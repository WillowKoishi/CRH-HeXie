package willow.train.hexie.compat.sable;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import willow.train.hexie.Main;
import willow.train.hexie.client.interaction.SurfaceInputSources;

/** Rendered plot coordinates are supplied from compatibility code, never selected by shared picking. */
@EventBusSubscriber(modid = Main.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SableControlInputClient {
    private SableControlInputClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SurfaceInputSources.registerEye(SableInteractionCoordinates::renderEye);
            SurfaceInputSources.registerFrame(target -> {
                if (target.carrier() != null || !(target.blocks() instanceof Level level)
                        || !SableInteractionCoordinates.contains(level, target.position())) return null;
                var frame = SableInteractionCoordinates.render(level, target.position(), target.partialTick());
                return new SurfaceInputSources.Frame() {
                    @Override public Vec3 toWorld(Vec3 point) { return frame.toWorld(point); }
                    @Override public SurfaceInputSources.Ray toLocal(Vec3 from, Vec3 to) {
                        var ray = frame.ray(from, to);
                        return new SurfaceInputSources.Ray(ray.from(), ray.to());
                    }
                };
            });
        });
    }
}
