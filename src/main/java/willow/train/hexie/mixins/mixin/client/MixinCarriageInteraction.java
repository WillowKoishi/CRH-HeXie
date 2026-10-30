package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.client.CarriageInteractionCoordinates;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

@Mixin(ContraptionHandlerClient.class)
public abstract class MixinCarriageInteraction {
    @WrapOperation(method = "getRayInputs", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"), remap = false)
    private static double hexie$missDoesNotOcclude(Vec3 hitLocation, Vec3 origin, Operation<Double> original) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        // A vanilla MISS endpoint belongs to the unshifted player ray, not to the
        // rendered camera ray. It is not an obstruction and must not shorten reach.
        if (minecraft.player != null
                && willow.train.hexie.compat.create.CarriagePlayerSupport.carriage(minecraft.player) != null
                && minecraft.hitResult != null
                && minecraft.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
            return minecraft.player.blockInteractionRange();
        return original.call(hitLocation, origin);
    }

    @WrapOperation(method = "getRayInputs", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition()Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private static Vec3 hexie$renderedEye(LocalPlayer player, Operation<Vec3> original) {
        if (willow.train.hexie.compat.create.CarriagePlayerSupport.carriage(player) == null)
            return original.call(player);
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.getEntity() == player && !camera.isDetached()) return camera.getPosition();
        float partial = CarriageInteractionCoordinates.partialTick();
        return player.getEyePosition(partial).add(
                willow.train.hexie.compat.create.CarriagePlayerSupport.renderOffset(player, partial));
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "rayTraceContraption", remap = false)
    private static net.minecraft.world.phys.BlockHitResult hexie$nativeCarriageRay(Vec3 origin, Vec3 target,
            AbstractContraptionEntity entity, Operation<net.minecraft.world.phys.BlockHitResult> original) {
        if (!(entity instanceof SnapshotCarriageEntity car)) return original.call(origin, target, entity);
        var compatibility = CarriageInteractionCoordinates.compatibilityEntity(car);
        // Convert the entire ray before native traversal/shape clipping. The result is
        // already a native local block/face and goes straight to Create's interaction packet.
        return original.call(CarriageInteractionCoordinates.toCompatibility(car, compatibility, origin),
                CarriageInteractionCoordinates.toCompatibility(car, compatibility, target), compatibility);
    }

    @WrapOperation(method = "rightClickingOnContraptionsGetsHandledLocally", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toGlobalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private static Vec3 hexie$renderedDistance(AbstractContraptionEntity entity, Vec3 local, float partial, Operation<Vec3> original) {
        return entity instanceof SnapshotCarriageEntity ? CarriageInteractionCoordinates.toWorld(entity, local)
                : original.call(entity, local, partial);
    }
}
