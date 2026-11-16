package willow.train.hexie.mixins.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Backports Sodium e423f071: discarded translucent quads must release their four-vertex buffer. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkVertexConsumer", remap = false)
public abstract class MixinSodiumChunkVertexConsumer {
    @Shadow(remap = false) private int vertexIndex;

    @Inject(method = "potentiallyEndVertex", at = @At("RETURN"), remap = false, require = 1)
    private void hexie$resetDiscardedQuad(CallbackInfoReturnable<VertexConsumer> callback) {
        // Four is a completed quad; preserve unfinished quads and upstream's normal reset.
        if (vertexIndex == 4) vertexIndex = 0;
    }
}
