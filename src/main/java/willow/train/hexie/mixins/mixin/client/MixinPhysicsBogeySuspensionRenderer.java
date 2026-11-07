package willow.train.hexie.mixins.mixin.client;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.client.render.bogey.BogeySuspensionPose;
import willow.train.hexie.client.render.bogey.SimurailBogeySuspensionPose;

@Pseudo
@Mixin(targets = "com.crystaelix.simurail.content.bogey.PhysicsBogeyRenderer", remap = false)
public abstract class MixinPhysicsBogeySuspensionRenderer {
    @WrapOperation(method = "renderSafe", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/bogey/BogeyStyle;render(Lcom/simibubi/create/content/trains/bogey/BogeySizes$BogeySize;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IIFLnet/minecraft/nbt/CompoundTag;Z)V"), require = 1)
    private void hexie$bodyMountedSuspension(BogeyStyle style, BogeySizes.BogeySize size, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay, float angle, CompoundTag data,
            boolean inContraption, Operation<Void> original, @Local(argsOnly = true) PhysicsBogeyBlockEntity bogey) {
        try (var scope = BogeySuspensionPose.push(SimurailBogeySuspensionPose.relative(bogey, partialTick))) {
            original.call(style, size, partialTick, pose, buffers, light, overlay, angle, data, inContraption);
        }
    }
}
