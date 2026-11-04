package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Carriage.class, remap = false)
public interface CreateCarriageAccessor {
    /** Existing entity payload, retained by Create while the carriage is unloaded. */
    @Accessor("serialisedEntity")
    CompoundTag hexie$getSerialisedEntity();
}
