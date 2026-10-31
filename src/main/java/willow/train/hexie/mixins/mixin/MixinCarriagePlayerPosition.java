package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import willow.train.hexie.compat.create.CarriagePlayerSupport;

/** Record resolved walking, gravity and collision displacement in the owned local frame. */
@Mixin(Entity.class)
public abstract class MixinCarriagePlayerPosition {
    @WrapMethod(method = "setPosRaw")
    private void hexie$localDisplacement(double x, double y, double z, Operation<Void> original) {
        if (!((Object) this instanceof Player player)) {
            original.call(x, y, z);
            return;
        }
        var before = player.position();
        original.call(x, y, z);
        CarriagePlayerSupport.moved(player, player.position().subtract(before));
    }
}
