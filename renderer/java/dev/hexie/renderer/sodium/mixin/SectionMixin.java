package dev.hexie.renderer.sodium.mixin;

import dev.hexie.renderer.mc.ClientRenderState;
import net.caffeinemc.mods.sodium.client.render.chunk.*;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=RenderSectionManager.class,remap=false)
abstract class SectionMixin {
    @Inject(method="updateSectionInfo(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)Z",at=@At("RETURN"))
    private void hexie$published(RenderSection section,BuiltSectionInfo info,CallbackInfoReturnable<Boolean> callback) {
        if (section == null) return;
        ClientRenderState.onSectionPublished(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }
}
