package dev.hexie.renderer.sable.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import dev.hexie.renderer.sable.ShipPrograms;
import java.io.InputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=Program.class,remap=false)
abstract class ShipShaderMixin {
    @ModifyExpressionValue(method="compileShaderInternal(Lcom/mojang/blaze3d/shaders/Program$Type;Ljava/lang/String;Ljava/io/InputStream;Ljava/lang/String;Lcom/mojang/blaze3d/preprocessor/GlslPreprocessor;)I",
        at=@At(value="INVOKE",target="Lorg/apache/commons/io/IOUtils;toString(Ljava/io/InputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;"))
    private static String hexie$shipShader(String source,Program.Type type,String name,InputStream stream,String pack,GlslPreprocessor preprocessor) {
        return ShipPrograms.patch(source,name,type==Program.Type.VERTEX);
    }
}
