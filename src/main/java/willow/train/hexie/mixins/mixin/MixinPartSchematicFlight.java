package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.compat.create.PartSchematicPlacement;

/** Keep the original serialized flight on obstruction; retrying never consumes or creates another item. */
@Mixin(value=LaunchedItem.class,remap=false)
public abstract class MixinPartSchematicFlight {
    @Shadow public int ticksRemaining;

    @Inject(method="update",at=@At("HEAD"),cancellable=true)
    private void hexie$completePart(Level level,CallbackInfoReturnable<Boolean> cir) {
        if(level.isClientSide||ticksRemaining>0||!((Object)this instanceof LaunchedItem.ForBlockState flight))return;
        var result=PartSchematicPlacement.placeResult(level,flight.target,flight.state,flight.stack,flight.data);
        if(result!=PartSchematicPlacement.Result.UNHANDLED)
            cir.setReturnValue(result==PartSchematicPlacement.Result.INSTALLED);
    }
}
