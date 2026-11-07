package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import willow.train.hexie.api.interaction.BlockControlPicking;
import willow.train.hexie.compat.sable.SableInteractionCoordinates;

/** Only selection rays opt into control geometry; collision, light and AI clips stay native. */
@Mixin(Entity.class)
public abstract class MixinBlockControlPicking {
    @WrapMethod(method = "pick")
    private HitResult hexie$pickControls(double reach, float partial, boolean fluids, Operation<HitResult> original) {
        var entity = (Entity) (Object) this;
        return BlockControlPicking.query(partial, () -> entity.level().isClientSide
                ? SableInteractionCoordinates.withRenderPose(entity.level(), partial, () -> original.call(reach, partial, fluids))
                : original.call(reach, partial, fluids));
    }
}
