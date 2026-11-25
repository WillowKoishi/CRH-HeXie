package dev.hexie.renderer.flywheel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.hexie.renderer.flywheel.FlywheelPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.engine_room.flywheel.backend.compile.core.Compilation", remap = false)
abstract class FlywheelCompilationMixin {
    @WrapOperation(
            method = "compile(Ldev/engine_room/flywheel/backend/gl/shader/ShaderType;Ljava/lang/String;)Ldev/engine_room/flywheel/backend/compile/core/ShaderResult;",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/gl/GlCompat;safeShaderSource(ILjava/lang/CharSequence;)V"),
            require = 0)
    private void hexie$patchFlywheelShader(int handle, CharSequence source, Operation<Void> original) {
        original.call(handle, FlywheelPrograms.patchFragment(source));
    }
}
