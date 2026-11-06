package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.actors.seat.ContraptionPlayerPassengerRotation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.client.events.CarriageCamera;

@Mixin(value = ContraptionPlayerPassengerRotation.class, remap = false)
public abstract class MixinCreatePassengerRotation {
    @WrapOperation(method = "frame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isPassenger()Z"), require = 1, allow = 1)
    private static boolean hexie$useCreateRotation(Player player, Operation<Boolean> original) {
        // Create clears its previous seat ID on this branch before it accumulates any rotation.
        return original.call(player) && !CarriageCamera.followsSeat(player);
    }
}
