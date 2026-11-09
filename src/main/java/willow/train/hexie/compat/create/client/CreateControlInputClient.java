package willow.train.hexie.compat.create.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import willow.train.hexie.Main;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.client.CarriageInteractionCoordinates;
import willow.train.hexie.client.interaction.SurfaceInputSources;
import willow.train.hexie.compat.create.ContraptionInteractionTargets;

/** Supplies existing Create carriers to shared input without changing their native interaction lifecycle. */
@EventBusSubscriber(modid = Main.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CreateControlInputClient {
    private CreateControlInputClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SurfaceInputSources.registerBlocker(() -> ControlsHandler.getContraption() != null);
            SurfaceInputSources.registerSource(CreateControlInputClient::collect);
            SurfaceInputSources.registerEyeOffset(CarriageInteractionCoordinates::renderEyeOffset);
            SurfaceInputSources.registerFrame(target -> {
                if (!(target.carrier() instanceof AbstractContraptionEntity carrier)) return null;
                return new SurfaceInputSources.Frame() {
                    @Override public Vec3 toWorld(Vec3 point) { return CarriageInteractionCoordinates.toWorld(carrier, point); }
                    @Override public SurfaceInputSources.Ray toLocal(Vec3 from, Vec3 to) {
                        var ray = CarriageInteractionCoordinates.toCompatibilityRay(carrier, carrier, from, to, target.partialTick());
                        return new SurfaceInputSources.Ray(ray.localFrom(), ray.localTo());
                    }
                };
            });
        });
    }
    private static void collect(Minecraft client, SurfaceInputSources.Ray ray, float partial, SurfaceInputSources.Query query) {
        var bounds = new AABB(ray.from(), ray.to()).inflate(16);
        for (var reference : ContraptionHandler.loadedContraptions.get(client.level).values()) {
            var carrier = reference.get();
            if (carrier == null || carrier.isRemoved() || carrier.getContraption() == null
                    || !carrier.getBoundingBox().intersects(bounds)) continue;
            var nativeHit = ContraptionHandlerClient.rayTraceContraption(ray.from(), ray.to(), carrier);
            if (nativeHit != null) query.obstruct(carrier.getId(), nativeHit.getBlockPos(),
                    CarriageInteractionCoordinates.toWorld(carrier, nativeHit.getLocation()));
            var local = CarriageInteractionCoordinates.toCompatibilityRay(carrier, carrier, ray.from(), ray.to(), partial);
            var candidates = SurfaceInputSources.cells(local.localFrom(), local.localTo());
            candidates.addAll(ContraptionInteractionTargets.query(carrier.getContraption(), local.localFrom(), local.localTo()));
            // Native picking includes the existing animated coupler pose list, beyond static block bounds.
            if (nativeHit != null) candidates.add(nativeHit.getBlockPos());
            for (var owner : candidates) {
                if (!carrier.getContraption().getBlocks().containsKey(owner) || carrier.getContraption().isHiddenInPortal(owner)) continue;
                query.add(new BlockControlContext(carrier.getContraption().getContraptionWorld(), owner, carrier, partial), nativeHit);
            }
        }
    }
}
