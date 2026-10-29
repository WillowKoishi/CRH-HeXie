package willow.train.hexie.mixins.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.client.render.track.TrackBallastRenderer;

@Mixin(value = TrackRenderer.class, remap = false)
public abstract class MixinTrackBallastRenderer {
    @Inject(method = "renderSafe(Lcom/simibubi/create/content/trains/track/TrackBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("HEAD"))
    private void hexie$renderBallast(TrackBlockEntity be, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        TrackBallastRenderer.render(be, pose, buffers);
    }
}
