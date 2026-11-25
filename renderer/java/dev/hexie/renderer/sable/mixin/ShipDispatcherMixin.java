package dev.hexie.renderer.sable.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.MeshData;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.hexie.renderer.Vec;
import dev.hexie.renderer.mc.*;
import dev.ryanhcode.sable.sublevel.render.dispatcher.*;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.minecraft.client.renderer.*;
import org.joml.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=VanillaSubLevelRenderDispatcher.class,remap=false)
abstract class ShipDispatcherMixin {
    @WrapOperation(method="renderSectionLayer(Ljava/lang/Iterable;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/ShaderInstance;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;F)V",
        at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/sublevel/render/vanilla/VanillaChunkedSubLevelRenderData;renderChunkedSubLevel(Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/ShaderInstance;Lorg/joml/Matrix4f;DDD)V"))
    private void hexie$ship(VanillaChunkedSubLevelRenderData data,RenderType layer,ShaderInstance shader,Matrix4f matrix,
            double x,double y,double z,Operation<Void> original) {
        Runnable draw=()->original.call(data,layer,shader,matrix,x,y,z);
        if (!((Object)this instanceof ReachAroundSubLevelRenderDispatcher) || ClientRenderState.demo==null ||
                ClientRenderState.engine().snapshot()==null || ClientRenderState.engine().lighting().lights()==0) {
            draw.run(); return;
        }
        var frame=ClientRenderState.engine().snapshot().frame();
        var pose=data.getSubLevel().renderPose(frame.partialTick());
        var rotation=new Matrix3f().rotation(new Quaternionf(pose.orientation())).get(new float[9]);
        ClientRenderState.shipBatches++;
        var receiver=new RendererBackend.Receiver(data.getSubLevel().getUniqueId(),layer,matrix,new Vec(x,y,z),rotation,false);
        ClientRenderState.lighting().drawReceiver(receiver,draw);
    }
    // Sable has already transformed each single-block ship's vertices into view space.
    // Undo only that view transform for the combined draw.
    @WrapOperation(method="renderAfterSections(Ljava/lang/Iterable;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;F)V",
        at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/RenderType;draw(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void hexie$single(RenderType layer,MeshData mesh,Operation<Void> original,Iterable<ClientSubLevel> ships,
            double x,double y,double z,Matrix4f modelView,Matrix4f projection,float partial) {
        Runnable draw=()->original.call(layer,mesh);
        if (!((Object)this instanceof ReachAroundSubLevelRenderDispatcher) || ClientRenderState.lighting().lights()==0) {
            draw.run(); return;
        }
        var inverse=new Matrix4f(modelView).invert();
        var camera=new Vec(x+inverse.m30(),y+inverse.m31(),z+inverse.m32());
        var receiver=new RendererBackend.Receiver(ViewPassOwnership.Owner.SABLE,layer,modelView,camera,
                inverse.get3x3(new Matrix3f()).get(new float[9]),false);
        ClientRenderState.shipBatches++;
        ClientRenderState.lighting().drawReceiver(receiver,draw);
    }
}
