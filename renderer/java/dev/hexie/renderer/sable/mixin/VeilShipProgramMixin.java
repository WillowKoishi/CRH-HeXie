package dev.hexie.renderer.sable.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.hexie.renderer.sable.ShipPrograms;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;

@Pseudo
@Mixin(targets="foundry.veil.impl.client.render.shader.processor.VanillaShaderProcessor",remap=false)
abstract class VeilShipProgramMixin {
    @ModifyReturnValue(method="modify(Ljava/util/Map;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;IILjava/lang/String;Lorg/lwjgl/opengl/GLCapabilities;)Ljava/lang/String;",at=@At("RETURN"))
    private static String hexie$recompiled(String result,Map<String,Object> data,String name,ResourceLocation path,
            VertexFormat format,int buffers,int type,String source,GLCapabilities caps) {
        return name==null?result:ShipPrograms.patch(result,name,type==GL_VERTEX_SHADER);
    }
}
