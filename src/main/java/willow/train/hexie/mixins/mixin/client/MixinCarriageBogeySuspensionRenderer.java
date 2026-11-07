package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.client.render.bogey.BogeySuspensionPose;
import willow.train.hexie.client.render.bogey.CreateBogeySuspensionPose;

@Mixin(value = CarriageContraptionEntityRenderer.class, remap = false)
public abstract class MixinCarriageBogeySuspensionRenderer {
    @WrapOperation(method = "lambda$render$1", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/bogey/BogeyStyle;render(Lcom/simibubi/create/content/trains/bogey/BogeySizes$BogeySize;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IIFLnet/minecraft/nbt/CompoundTag;Z)V"), require = 1)
    private static void hexie$bodyMountedSuspension(BogeyStyle style, BogeySizes.BogeySize size, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay, float angle, CompoundTag data,
            boolean inContraption, Operation<Void> original,
            @Local(argsOnly = true) CarriageContraptionEntity entity, @Local(argsOnly = true) CarriageBogey bogey) {
        try (var scope = BogeySuspensionPose.push(CreateBogeySuspensionPose.relative(entity, bogey, partialTick))) {
            original.call(style, size, partialTick, pose, buffers, light, overlay, angle, data, inContraption);
        }
    }
}
