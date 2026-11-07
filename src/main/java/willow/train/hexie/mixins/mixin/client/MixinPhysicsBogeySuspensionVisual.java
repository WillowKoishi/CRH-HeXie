package willow.train.hexie.mixins.mixin.client;

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
import willow.train.hexie.client.render.bogey.BogeySuspensionPose;
import willow.train.hexie.client.render.bogey.SimurailBogeySuspensionPose;

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
        try (var scope = BogeySuspensionPose.push(SimurailBogeySuspensionPose.relative(blockEntity, context.partialTick()))) {
            original.call(visual, data, angle, pose);
        }
    }
}
