package willow.train.hexie.compat.mixins.client.sable;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.bridge.HexieBridges;

/** Single-block plots bypass section compilation; an instanced shell already supplies their model. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData", remap = false)
public abstract class MixinSableSingleBlockShell {
    @Shadow(remap = false) private BlockPos singleBlockPos;

    @Inject(method = "renderSingleBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void hexie$skipInstancedShell(CallbackInfo ci) {
        if (HexieBridges.Client.carriageRender().crh2StaticSections().instanced(singleBlockPos)
                || HexieBridges.Client.carriageRender().crh1StaticSections().instanced(singleBlockPos)) ci.cancel();
    }
}
