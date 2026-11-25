package willow.train.hexie.compat.mixins.player;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import willow.train.hexie.compat.create.motion.CarriageChunkAccess;

@Mixin(Player.class)
public abstract class MixinCarriagePlayerTick {
    @WrapMethod(method = "tick")
    private void hexie$tickWithAvailableTerrain(Operation<Void> original) {
        var previous = CarriageChunkAccess.enter((Player) (Object) this);
        try { original.call(); }
        finally { CarriageChunkAccess.exit(previous); }
    }
}
