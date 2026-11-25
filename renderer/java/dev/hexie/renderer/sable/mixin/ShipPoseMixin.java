package dev.hexie.renderer.sable.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.hexie.renderer.mc.ClientRenderState;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=VanillaChunkedSubLevelRenderData.class,remap=false)
abstract class ShipPoseMixin {
    @WrapOperation(method="renderChunkedSubLevel(Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/ShaderInstance;Lorg/joml/Matrix4f;DDD)V",
        at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/sublevel/ClientSubLevel;renderPose()Ldev/ryanhcode/sable/companion/math/Pose3dc;"))
    private Pose3dc hexie$frozen(ClientSubLevel ship,Operation<Pose3dc> original) {
        var snapshot=ClientRenderState.demo==null?null:ClientRenderState.engine().snapshot();
        return snapshot==null?original.call(ship):ship.renderPose(snapshot.frame().partialTick());
    }
}
