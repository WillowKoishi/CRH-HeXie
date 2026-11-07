package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.api.interaction.WorldInteractionTargets;

/** Sable emits chunk-load events, but its plot removal bypasses ChunkEvent.Unload. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.LevelPlot", remap = false)
public abstract class MixinSablePlotInteractionTargets {
    @WrapOperation(method = "onRemove", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;setLoaded(Z)V", remap = true),
            remap = false, require = 1)
    private void hexie$discardChunkTargets(LevelChunk chunk, boolean loaded, Operation<Void> original) {
        original.call(chunk, loaded);
        if (!loaded) WorldInteractionTargets.unloaded(chunk.getLevel(), chunk.getPos());
    }
}
