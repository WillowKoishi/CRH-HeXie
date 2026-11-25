package willow.train.hexie.compat.create.interaction;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge.SurfaceInputSourcesFrameView;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge.SurfaceInputSourcesQueryView;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge.SurfaceInputSourcesRayView;
import willow.train.hexie.core.api.interaction.BlockControlContext;

/** Supplies existing Create carriers to shared input without changing their native interaction lifecycle. */
public final class CreateControlInputClient {
    private CreateControlInputClient() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            HexieBridges.surfaceInput().surfaceInputSources().registerBlocker(() -> ControlsHandler.getContraption() != null);
            HexieBridges.Client.surfaceInputClient().surfaceInputSources().registerSource(CreateControlInputClient::collect);
            HexieBridges.surfaceInput().surfaceInputSources().registerEyeOffset(HexieBridges.Client.render().carriageInteractionCoordinates()::renderEyeOffset);
            HexieBridges.surfaceInput().surfaceInputSources().registerFrame(target -> {
                if (!(target.carrier() instanceof AbstractContraptionEntity carrier)) return null;
                return new SurfaceInputSourcesFrameView() {
                    @Override public Vec3 toWorld(Vec3 point) { return HexieBridges.Client.render().carriageInteractionCoordinates().toWorld(carrier, point); }
                    @Override public SurfaceInputSourcesRayView toLocal(Vec3 from, Vec3 to) {
                        var ray = HexieBridges.Client.render().carriageInteractionCoordinates().toCompatibilityRay(carrier, carrier, from, to, target.partialTick());
                        return HexieBridges.surfaceInput().createSurfaceInputSourcesRay(ray.localFrom(), ray.localTo());
                    }
                };
            });
        });
    }
    private static void collect(Minecraft client, SurfaceInputSourcesRayView ray, float partial, SurfaceInputSourcesQueryView query) {
        var bounds = new AABB(ray.from(), ray.to()).inflate(16);
        for (var reference : ContraptionHandler.loadedContraptions.get(client.level).values()) {
            var carrier = reference.get();
            if (carrier == null || carrier.isRemoved() || carrier.getContraption() == null
                    || !carrier.getBoundingBox().intersects(bounds)) continue;
            var nativeHit = ContraptionHandlerClient.rayTraceContraption(ray.from(), ray.to(), carrier);
            if (nativeHit != null) query.obstruct(carrier.getId(), nativeHit.getBlockPos(),
                    HexieBridges.Client.render().carriageInteractionCoordinates().toWorld(carrier, nativeHit.getLocation()));
            var local = HexieBridges.Client.render().carriageInteractionCoordinates().toCompatibilityRay(carrier, carrier, ray.from(), ray.to(), partial);
            var candidates = HexieBridges.surfaceInput().surfaceInputSources().cells(local.localFrom(), local.localTo());
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
