package willow.train.hexie.mixins.mixin.client;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.client.render.CRH2StaticSections;

/** Single-block plots bypass section compilation; an instanced shell already supplies their model. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData", remap = false)
public abstract class MixinSableSingleBlockShell {
    @Shadow(remap = false) private BlockPos singleBlockPos;

    @Inject(method = "renderSingleBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void hexie$skipInstancedShell(CallbackInfo ci) {
        if (CRH2StaticSections.instanced(singleBlockPos)) ci.cancel();
    }
}
