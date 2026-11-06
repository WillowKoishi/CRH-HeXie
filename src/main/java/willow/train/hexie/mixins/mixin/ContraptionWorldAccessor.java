package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ContraptionWorld.class, remap = false)
public interface ContraptionWorldAccessor {
    @Accessor("contraption")
    Contraption hexie$getContraption();
}
