package willow.train.hexie.mixins.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.api.interaction.WorldInteractionTargets;

/** Maintains only the changed world's extended interaction owner. */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunkInteraction {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void hexie$interactionStateChanged(BlockPos position, BlockState requested, boolean moving,
            CallbackInfoReturnable<BlockState> cir) {
        // onRemove/onPlace can replace the requested state again. Read the final
        // state directly from this chunk, including early/null return paths.
        WorldInteractionTargets.changed((LevelChunk) (Object) this, position);
    }
}
