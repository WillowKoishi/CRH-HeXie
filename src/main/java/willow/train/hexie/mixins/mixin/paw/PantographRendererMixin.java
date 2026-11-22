package willow.train.hexie.mixins.mixin.paw;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.compat.cee.PantographMounting;

/** Lowers P&W's animated model with the block entity's support roof. */
@Pseudo
@Mixin(targets = "de.mrjulsen.paw.blockentity.client.PantographBlockRenderer", remap = false)
public abstract class PantographRendererMixin {
    private static final String RENDER_METHOD = "defaultRender(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lde/mrjulsen/paw/blockentity/PantographBlockEntity;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;FFI)V";

    @Inject(method = RENDER_METHOD, at = @At("HEAD"))
    private void hexie$lowerModel(PoseStack pose, @Coerce BlockEntity entity, MultiBufferSource buffers,
                                  RenderType renderType, com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                  float yaw, float partialTick, int light, CallbackInfo callback) {
        pose.pushPose();
        if (entity.getLevel() != null)
            pose.translate(0, PantographMounting.offset(entity.getLevel(), entity.getBlockPos()), 0);
    }

    @Inject(method = RENDER_METHOD, at = @At("RETURN"))
    private void hexie$restorePose(PoseStack pose, @Coerce BlockEntity entity, MultiBufferSource buffers,
                                   RenderType renderType, com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                   float yaw, float partialTick, int light, CallbackInfo callback) {
        pose.popPose();
    }
}
