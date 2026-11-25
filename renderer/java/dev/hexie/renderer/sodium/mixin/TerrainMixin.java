package dev.hexie.renderer.sodium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import dev.hexie.renderer.Vec;
import dev.hexie.renderer.mc.*;
import dev.hexie.renderer.sodium.TerrainPrograms;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.gl.device.*;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlTessellation;
import net.caffeinemc.mods.sodium.client.render.chunk.*;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=DefaultChunkRenderer.class,remap=false)
abstract class TerrainMixin extends ShaderChunkRenderer {
    private TerrainMixin(RenderDevice device,ChunkVertexType type) { super(device,type); }
    @Unique private static final float[] HEXIE_IDENTITY={1,0,0,0,1,0,0,0,1};
    @WrapOperation(method="render(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderListIterable;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Z)V",
        at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V"))
    private void hexie$batch(CommandList commands,GlTessellation tess,MultiDrawBatch batch,Operation<Void> draw,
            @Local(argsOnly=true) CameraTransform camera,@Local(argsOnly=true) TerrainRenderPass pass,
            @Local(argsOnly=true) ChunkRenderMatrices matrices) {
        ClientRenderState.terrainBatches++;
        var receiver=new RendererBackend.Receiver(ViewPassOwnership.Owner.WORLD,pass,matrices,
                new Vec(camera.x,camera.y,camera.z),HEXIE_IDENTITY,TerrainPrograms.veilNormals(vertexFormat));
        var lighting = ClientRenderState.lighting();
        if (lighting == RendererBackend.Lighting.OFF) {
            ForwardLight.draw(receiver.camera(), receiver.rotation(), receiver.packedNormal(),
                    () -> draw.call(commands, tess, batch));
        } else {
            lighting.drawReceiver(receiver, () -> draw.call(commands, tess, batch));
        }
    }
}
