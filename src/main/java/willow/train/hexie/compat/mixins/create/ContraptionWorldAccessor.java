package willow.train.hexie.compat.mixins.create;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import willow.train.hexie.bridge.compat.CreateCarriageBridge.ContraptionWorldAccessorView;

@Mixin(value = ContraptionWorld.class, remap = false)
public interface ContraptionWorldAccessor extends ContraptionWorldAccessorView {
    @Accessor("contraption")
    Contraption hexie$getContraption();
}
