package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keep a changed visual field in the spawn payload as well as the full saved block data. */
@Mixin(Contraption.class)
public interface ContraptionUpdateTagsAccessor {
    @Accessor(value = "updateTags", remap = false)
    Map<BlockPos, CompoundTag> hexie$getUpdateTags();
}
