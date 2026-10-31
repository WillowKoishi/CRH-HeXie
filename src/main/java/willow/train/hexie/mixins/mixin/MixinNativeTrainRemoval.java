package willow.train.hexie.mixins.mixin;

import java.util.UUID;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.compat.create.CompatibilityTrain;

@Mixin(value = GlobalRailwayManager.class, remap = false)
public abstract class MixinNativeTrainRemoval {
    @Inject(method = "removeTrain", at = @At("HEAD"))
    private void hexie$removeSourceCarriages(UUID id, CallbackInfo ci) {
        if ((Object) this == Create.RAILWAYS
                && Create.RAILWAYS.trains.get(id) instanceof CompatibilityTrain train)
            train.queueSourceRemoval();
    }
}
