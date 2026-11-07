package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.api.interaction.BlockControlHost;
import willow.train.hexie.client.CarriageControlPicking;
import willow.train.hexie.client.CarriageInteractionCoordinates;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

@Mixin(ContraptionHandlerClient.class)
public abstract class MixinCarriageInteraction {
    @Unique private static final ThreadLocal<CarriageControlPicking> hexie$pickingCarriage = new ThreadLocal<>();

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
    private static BlockHitResult hexie$nativeCarriageRay(Vec3 origin, Vec3 target,
            AbstractContraptionEntity entity, Operation<BlockHitResult> original) {
        if (entity.getContraption() == null) return original.call(origin, target, entity);
        var compatibility = entity;
        if (entity instanceof SnapshotCarriageEntity car)
            compatibility = CarriageInteractionCoordinates.compatibilityEntity(car);
        float partial = CarriageInteractionCoordinates.partialTick();
        var ray = CarriageInteractionCoordinates.toCompatibilityRay(entity, compatibility, origin, target, partial);
        origin = ray.from();
        target = ray.to();
        // Convert the entire ray before native traversal/shape clipping. The result is
        // already a native local block/face and goes straight to Create's interaction packet.
        var picking = new CarriageControlPicking(entity, ray.localFrom(), ray.localTo(), partial);
        var previous = hexie$pickingCarriage.get();
        hexie$pickingCarriage.set(picking);
        try {
            return picking.nearest(original.call(origin, target, compatibility));
        } finally {
            if (previous == null) hexie$pickingCarriage.remove();
            else hexie$pickingCarriage.set(previous);
        }
    }

    @WrapOperation(method = "lambda$rayTraceContraption$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"), remap = false)
    private static VoxelShape hexie$carriageCandidateShape(BlockState state, BlockGetter level, BlockPos ignored,
            Operation<VoxelShape> original, @Local(ordinal = 1) BlockPos position) {
        return original.call(state, level,
                BlockControlHost.find(state) != null ? position : ignored);
    }

    @WrapOperation(method = "lambda$rayTraceContraption$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/VoxelShape;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/BlockHitResult;"), remap = false)
    private static BlockHitResult hexie$carriageCandidateClip(VoxelShape shape, Vec3 from, Vec3 to,
            BlockPos position, Operation<BlockHitResult> original, @Local BlockState state) {
        var picking = hexie$pickingCarriage.get();
        return picking != null && picking.replacesNative(position, state) ? null
                : original.call(shape, from, to, position);
    }

    @WrapOperation(method = "rightClickingOnContraptionsGetsHandledLocally", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toGlobalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private static Vec3 hexie$renderedDistance(AbstractContraptionEntity entity, Vec3 local, float partial, Operation<Vec3> original) {
        return entity instanceof SnapshotCarriageEntity ? CarriageInteractionCoordinates.toWorld(entity, local)
                : original.call(entity, local, partial);
    }
}
