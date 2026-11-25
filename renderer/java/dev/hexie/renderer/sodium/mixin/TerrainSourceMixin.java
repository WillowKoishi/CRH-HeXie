package dev.hexie.renderer.sodium.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.hexie.renderer.sodium.TerrainPrograms;
import net.caffeinemc.mods.sodium.client.gl.shader.*;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=GlShader.class,remap=false)
abstract class TerrainSourceMixin {
    @ModifyExpressionValue(method="<init>(Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderType;Lnet/minecraft/resources/ResourceLocation;Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderParser$ParsedShader;)V",
        at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderParser$ParsedShader;src()Ljava/lang/String;"))
    private String hexie$forward(String source,ShaderType type,ResourceLocation name,ShaderParser.ParsedShader parsed) {
        if (!name.getNamespace().equals("sodium")) return source;
        return switch(name.getPath()) {
            case "blocks/block_layer_opaque.vsh" -> TerrainPrograms.patch(source,true);
            case "blocks/block_layer_opaque.fsh" -> TerrainPrograms.patch(source,false);
            default -> source;
        };
    }
}
