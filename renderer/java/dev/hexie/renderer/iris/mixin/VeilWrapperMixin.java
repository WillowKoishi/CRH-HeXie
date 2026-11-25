package dev.hexie.renderer.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.hexie.renderer.iris.PassTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets="foundry.veil.impl.client.render.shader.program.ShaderProgramImpl$Wrapper",remap=false)
abstract class VeilWrapperMixin {
    @WrapMethod(method="apply()V")
    private void hexie$afterApply(Operation<Void> original) {
        PassTarget.wrapper(()->original.call());
    }
}
