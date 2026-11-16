package willow.train.hexie.mixins.mixin.cee;

import willow.train.hexie.compat.cee.PantographMounting;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlockEntity", remap = false)
public abstract class PantographMountMixin {
    @ModifyExpressionValue(method = {"handleOnClient", "handleOnServer"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;", ordinal = 0))
    private Vec3 hexie$moveSearch(Vec3 original) {
        BlockEntity entity = (BlockEntity) (Object) this;
        return entity.getLevel() == null ? original : original.add(0, PantographMounting.offset(entity.getLevel(), entity.getBlockPos()), 0);
    }
    @Inject(method = "getConnectorPos", at = @At("RETURN"), cancellable = true)
    private void hexie$moveContact(float extension, CallbackInfoReturnable<Vec3> callback) {
        BlockEntity entity = (BlockEntity) (Object) this;
        if (entity.getLevel() != null)
            callback.setReturnValue(callback.getReturnValue().add(0, PantographMounting.offset(entity.getLevel(), entity.getBlockPos()), 0));
    }
}
