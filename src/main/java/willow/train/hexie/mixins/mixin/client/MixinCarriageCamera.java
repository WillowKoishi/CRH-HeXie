package willow.train.hexie.mixins.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.compat.create.CarriagePlayerSupport;

@Mixin(Camera.class)
public abstract class MixinCarriageCamera {
    @Shadow public abstract Vec3 getPosition();
    @Shadow protected abstract void setPosition(Vec3 position);
    @Inject(method = "setup", at = @At("RETURN"))
    private void hexie$bodyRelativeCamera(BlockGetter level, Entity entity, boolean detached,
            boolean reverse, float partial, CallbackInfo ci) {
        if (entity instanceof Player player)
            setPosition(getPosition().add(CarriagePlayerSupport.renderOffset(player, partial)));
    }
}
