package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.client.events.CarriageStandingClient;

@Mixin(ClientPacketListener.class)
public abstract class MixinCarriageWorldTeleport {
    @WrapOperation(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;setPos(DDD)V"))
    private void hexie$applyWorldPosition(Player player, double x, double y, double z, Operation<Void> setPos) {
        // A world teleport, including a new seat mount, supersedes any queued
        // local seat exit/login placement and its pending movement report.
        CarriagePlayerSupport.worldTeleport(player);
        CarriageStandingClient.resetMovement(player);
        setPos.call(player, x, y, z);
    }
}
