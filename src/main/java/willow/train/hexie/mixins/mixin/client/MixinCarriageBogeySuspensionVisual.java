package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.bogey.BogeyVisual;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.client.render.bogey.BogeySuspensionPose;
import willow.train.hexie.client.render.bogey.CreateBogeySuspensionPose;

@Mixin(value = CarriageContraptionVisual.class, remap = false)
public abstract class MixinCarriageBogeySuspensionVisual extends AbstractEntityVisual<CarriageContraptionEntity> {
    protected MixinCarriageBogeySuspensionVisual(VisualizationContext context, CarriageContraptionEntity entity,
                                                float partialTick) {
        super(context, entity, partialTick);
    }

    @WrapOperation(method = "animate", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/bogey/BogeyVisual;update(Lnet/minecraft/nbt/CompoundTag;FLcom/mojang/blaze3d/vertex/PoseStack;)V"), require = 1)
    private void hexie$bodyMountedSuspension(BogeyVisual visual, CompoundTag data, float angle, PoseStack pose,
            Operation<Void> original, float partialTick, @Local CarriageBogey bogey) {
        try (var scope = BogeySuspensionPose.push(CreateBogeySuspensionPose.relative(entity, bogey, partialTick))) {
            original.call(visual, data, angle, pose);
        }
    }
}
