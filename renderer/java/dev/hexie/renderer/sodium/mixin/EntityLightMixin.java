package dev.hexie.renderer.sodium.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.hexie.renderer.Vec;
import dev.hexie.renderer.mc.ClientRenderState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=EntityRenderDispatcher.class,remap=false)
abstract class EntityLightMixin {
    @ModifyReturnValue(method="getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I",at=@At("RETURN"))
    private int hexie$packed(int original,Entity entity,float partial) {
        if (!(entity instanceof LivingEntity)) return original;
        var motion=entity.getPosition(partial).subtract(entity.position());
        var bounds=entity.getBoundingBoxForCulling().move(motion);
        int light=ClientRenderState.lighting().blockLight(new Vec(bounds.minX,bounds.minY,bounds.minZ),new Vec(bounds.maxX,bounds.maxY,bounds.maxZ));
        return light<=LightTexture.block(original)?original:LightTexture.pack(light,LightTexture.sky(original));
    }
}
