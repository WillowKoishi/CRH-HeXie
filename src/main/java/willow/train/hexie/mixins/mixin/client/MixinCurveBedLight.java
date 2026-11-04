package willow.train.hexie.mixins.mixin.client;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.client.render.track.BallastCurveBuffers;

/** Use the same completed light-section notifications that invalidate the vanilla chunk mesh. */
@Mixin(ClientChunkCache.class)
abstract class MixinCurveBedLight {
    @Shadow @Final ClientLevel level;
    @Inject(method = "onLightUpdate", at = @At("RETURN"))
    private void hexie$invalidateBedLight(LightLayer layer, SectionPos pos, CallbackInfo ci) {
        BallastCurveBuffers.lightChanged(level, pos);
    }
}
