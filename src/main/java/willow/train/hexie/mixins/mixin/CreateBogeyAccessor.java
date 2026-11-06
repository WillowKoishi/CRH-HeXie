package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CarriageBogey.class, remap = false)
public interface CreateBogeyAccessor {
    @Accessor("type")
    AbstractBogeyBlock<?> hexie$bogeyType();
}
