package willow.train.hexie.mixins.mixin.cee;

import willow.train.hexie.compat.cee.PantographMounting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographRenderer", remap = false)
public abstract class PantographRendererMixin {
    @Inject(method = "renderSafe(Lcom/george_vi/electroenergetics/content/railway_electrification/pantograph/PantographBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("HEAD"))
    private void hexie$lowerBase(@Coerce BlockEntity entity, float partialTicks, PoseStack pose, MultiBufferSource buffer,
                                  int light, int overlay, CallbackInfo callback) {
        pose.pushPose();
        if (entity.getLevel() != null) pose.translate(0, PantographMounting.offset(entity.getLevel(), entity.getBlockPos()), 0);
        willow.train.hexie.compat.cee.PantographBaseModels.render(entity.getBlockState(), pose, buffer, light, overlay);
    }

    @Inject(method = "renderSafe(Lcom/george_vi/electroenergetics/content/railway_electrification/pantograph/PantographBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("RETURN"))
    private void hexie$restorePose(@Coerce BlockEntity entity, float partialTicks, PoseStack pose, MultiBufferSource buffer,
                                    int light, int overlay, CallbackInfo callback) {
        pose.popPose();
    }
}
