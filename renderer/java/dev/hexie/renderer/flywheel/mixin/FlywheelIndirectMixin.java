package dev.hexie.renderer.flywheel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.hexie.renderer.flywheel.FlywheelPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup$MultiDraw", remap = false)
abstract class FlywheelIndirectMixin {
    @WrapOperation(
            method = "submit(Ldev/engine_room/flywheel/backend/gl/shader/GlProgram;)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/gl/GlCompat;safeMultiDrawElementsIndirect(Ldev/engine_room/flywheel/backend/gl/shader/GlProgram;IIIIJ)V"),
            require = 0)
    private void hexie$indirectDraw(@Coerce Object program, int mode, int type, int start, int end, long stride,
                                    Operation<Void> original) {
        FlywheelPrograms.draw(() -> original.call(program, mode, type, start, end, stride));
    }
}
