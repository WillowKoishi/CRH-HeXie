package willow.train.hexie.compat.mixins.cee.paw;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.cee.PantographMounting;

/** Applies the roof offset to P&W's catenary search position while the pantograph is moving. */
@Pseudo
@Mixin(targets = "de.mrjulsen.paw.blockentity.PantographMovementBehaviour", remap = false)
public abstract class PantographMovementMixin {
    @ModifyExpressionValue(method = "tick(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;)V",
            at = @At(value = "NEW", target = "(DDD)Lorg/joml/Vector3d;"), require = 0)
    private Vector3d hexie$lowerWireSearch(Vector3d position, MovementContext context) {
        double amount = PantographMounting.movingOffset(context);
        if (amount == 0) return position;
        Vec3 offset = context.rotation.apply(new Vec3(0, amount, 0));
        return position.add(offset.x, offset.y, offset.z);
    }
}
