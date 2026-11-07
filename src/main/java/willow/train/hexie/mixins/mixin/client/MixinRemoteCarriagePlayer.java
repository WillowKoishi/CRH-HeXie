package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.RemotePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriagePlayerSupport;

@Mixin(RemotePlayer.class)
public abstract class MixinRemoteCarriagePlayer {
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/RemotePlayer;lerpPositionAndRotationStep(IDDDDD)V"))
    private void hexie$relativePosition(RemotePlayer player, int steps, double x, double y, double z,
            double yaw, double pitch, Operation<Void> original) {
        if (CarriagePlayerSupport.carriage(player) != null) {
            x = player.getX();
            y = player.getY();
            z = player.getZ();
        }
        original.call(player, steps, x, y, z, yaw, pitch);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/RemotePlayer;calculateEntityAnimation(Z)V"))
    private void hexie$relativeWalking(RemotePlayer player, boolean flying, Operation<Void> original) {
        if (CarriagePlayerSupport.carriage(player) == null) {
            original.call(player, flying);
            return;
        }
        var movement = CarriagePlayerSupport.remoteMovement(player);
        float speed = (float) Math.min(movement.horizontalDistance() * 4, 1);
        player.walkAnimation.update(speed, .4f);
    }
}
