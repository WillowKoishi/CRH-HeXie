package willow.train.hexie.mixins.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.compat.simurail.PhysicalHeadAssembly;

/** Use Simulated's ordinary assembly-failure path when a physical head cannot move as a whole. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.util.assembly.SimAssemblyContraption", remap = false)
public abstract class MixinSimulatedPhysicalHeadAssembly {
    @Inject(method = "movementAllowed", at = @At("RETURN"), cancellable = true, require = 1)
    private void hexie$requireCompleteHead(BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ModList.get().isLoaded("simurail")
                && !PhysicalHeadAssembly.movementAllowed(state, level, pos)) cir.setReturnValue(false);
    }
}
