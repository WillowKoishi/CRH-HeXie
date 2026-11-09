package willow.train.hexie.mixins.mixin.client;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.compat.simurail.PhysicalHeadBogeyBlockEntity;

/** The kinetic shaft stays at the control; wheels and connectors share the real floor attachment origin. */
@Pseudo
@Mixin(targets = "com.crystaelix.simurail.content.bogey.PhysicsBogeyRenderer", remap = false)
public abstract class MixinPhysicalHeadBogeyRenderer {
    @WrapMethod(method = "renderSafe(Lcom/crystaelix/simurail/content/bogey/PhysicsBogeyBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V")
    private void hexie$restorePhysicalHeadOrigin(PhysicsBogeyBlockEntity bogey, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay, Operation<Void> original) {
        if (!(bogey instanceof PhysicalHeadBogeyBlockEntity)) {
            original.call(bogey, partialTick, pose, buffers, light, overlay);
            return;
        }
        pose.pushPose();
        try { original.call(bogey, partialTick, pose, buffers, light, overlay); }
        finally { pose.popPose(); }
    }

    @Inject(method = "renderSafe(Lcom/crystaelix/simurail/content/bogey/PhysicsBogeyBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At(value = "INVOKE",
            target = "Lcom/crystaelix/simurail/content/bogey/PhysicsBogeyBlockEntity;getRenderPivotOffset(FLorg/joml/Vector3f;)Lorg/joml/Vector3f;",
            ordinal = 0), cancellable = true, require = 1)
    private void hexie$physicalHeadOrigin(PhysicsBogeyBlockEntity bogey, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (!(bogey instanceof PhysicalHeadBogeyBlockEntity head)) return;
        if (head.isCarriageView() || !head.headBogeyEnabled()) { ci.cancel(); return; }
        var offset = head.attachmentOffset();
        pose.translate(offset.x, offset.y, offset.z);
    }
}
