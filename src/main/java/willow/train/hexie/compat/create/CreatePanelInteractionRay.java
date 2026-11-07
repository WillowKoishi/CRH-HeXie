package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import willow.train.hexie.client.CarriageInteractionCoordinates;

/** Use Create's finite, obstruction-clipped ray for all attachment tools. */
final class CreatePanelInteractionRay {
    record Pick(AbstractContraptionEntity carrier, BlockHitResult hit) {}
    static Pick pick(LocalPlayer player) {
        var ray = ContraptionHandlerClient.getRayInputs(player);
        var search = new AABB(ray.getFirst(), ray.getSecond()).inflate(16);
        double nearest = Double.MAX_VALUE;
        Pick result = null;
        for (var reference : ContraptionHandler.loadedContraptions.get(player.level()).values()) {
            var candidate = reference.get();
            if (candidate == null || candidate.getContraption() == null || !candidate.getBoundingBox().intersects(search)) continue;
            var hit = ContraptionHandlerClient.rayTraceContraption(ray.getFirst(), ray.getSecond(), candidate);
            if (hit == null) continue;
            double distance = CarriageInteractionCoordinates.toWorld(candidate, hit.getLocation()).distanceToSqr(ray.getFirst());
            if (distance >= nearest) continue;
            nearest = distance;
            result = new Pick(candidate, hit);
        }
        return result;
    }
    private CreatePanelInteractionRay() {}
}
