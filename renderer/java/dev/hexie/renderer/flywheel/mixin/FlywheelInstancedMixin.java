package dev.hexie.renderer.flywheel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.hexie.renderer.flywheel.FlywheelPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "dev.engine_room.flywheel.backend.engine.instancing.InstancedDraw", remap = false)
abstract class FlywheelInstancedMixin {
    @WrapOperation(
            method = "render(Ldev/engine_room/flywheel/backend/gl/TextureBuffer;)V",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/engine/MeshPool$PooledMesh;draw(I)V"),
            require = 0)
    private void hexie$instancedDraw(@Coerce Object mesh, int count, Operation<Void> original) {
        FlywheelPrograms.draw(() -> original.call(mesh, count));
    }
}
