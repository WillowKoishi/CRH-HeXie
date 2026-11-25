package willow.train.hexie.compat.mixins.cee.paw;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.compat.cee.PantographMounting;

/** Keeps the P&W pantograph's selectable/collision shape aligned with its lowered position. */
@Pseudo
@Mixin(targets = "de.mrjulsen.paw.block.PantographBlock", remap = false)
public abstract class PantographShapeMixin {
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void hexie$lowerShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                  CallbackInfoReturnable<VoxelShape> callback) {
        double offset = PantographMounting.offset(level, pos);
        if (offset != 0) callback.setReturnValue(callback.getReturnValue().move(0, offset, 0));
    }
}
