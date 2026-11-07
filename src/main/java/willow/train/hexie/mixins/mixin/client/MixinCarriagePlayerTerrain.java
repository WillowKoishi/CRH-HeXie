package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriagePlayerSupport;

@Mixin(LivingEntity.class)
public abstract class MixinCarriagePlayerTerrain {
    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;hasChunkAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean hexie$normalGravityOnCarriage(boolean loaded) {
        return loaded || (Object) this instanceof Player player && CarriagePlayerSupport.attachedCarriage(player) != null;
    }
}
