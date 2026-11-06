package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriagePlayerSupport;

@Mixin(ClientPacketListener.class)
public abstract class MixinCarriageWorldTeleport {
    @WrapOperation(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;setPos(DDD)V"))
    private void hexie$applyWorldPosition(Player player, double x, double y, double z, Operation<Void> setPos) {
        // Confirmed corrections on a carriage arrive in carriage coordinates. An ordinary
        // world teleport supersedes any local prediction, including a rejected first contact.
        CarriagePlayerSupport.detach(player);
        setPos.call(player, x, y, z);
    }
}
