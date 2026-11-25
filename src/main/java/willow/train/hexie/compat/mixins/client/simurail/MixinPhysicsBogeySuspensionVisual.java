package willow.train.hexie.compat.mixins.client.simurail;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.bogey.BogeyVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.WorldBlockBridge.BogeyFloorDataOperations;
import willow.train.hexie.compat.simurail.PhysicalHeadBogeyBlockEntity;

@Pseudo
@Mixin(targets = "com.crystaelix.simurail.content.bogey.PhysicsBogeyVisual", remap = false)
public abstract class MixinPhysicsBogeySuspensionVisual extends AbstractBlockEntityVisual<PhysicsBogeyBlockEntity> {
    protected MixinPhysicsBogeySuspensionVisual(VisualizationContext context, PhysicsBogeyBlockEntity entity,
                                               float partialTick) {
        super(context, entity, partialTick);
    }

    @WrapOperation(method = "beginFrame", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/bogey/BogeyVisual;update(Lnet/minecraft/nbt/CompoundTag;FLcom/mojang/blaze3d/vertex/PoseStack;)V"), require = 1)
    private void hexie$bodyMountedSuspension(BogeyVisual visual, CompoundTag data, float angle, PoseStack pose,
            Operation<Void> original, DynamicVisual.Context context) {
        if (blockEntity instanceof PhysicalHeadBogeyBlockEntity) {
            data = data.copy();
            data.putInt(BogeyFloorDataOperations.LONGITUDINAL_KEY, 0);
        }
        try (var scope = HexieBridges.Client.bogeyRender().bogeySuspensionPose().push(HexieBridges.Client.bogeyRender().simurailBogeySuspensionPose().relative(blockEntity, context.partialTick()))) {
            original.call(visual, data, angle, pose);
        }
    }
}
