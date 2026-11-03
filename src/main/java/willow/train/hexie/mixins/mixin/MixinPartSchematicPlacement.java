package willow.train.hexie.mixins.mixin;

import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BlockHelper.class,remap=false)
public abstract class MixinPartSchematicPlacement {
    @Inject(method="placeSchematicBlock",at=@At("HEAD"),cancellable=true)
    private static void hexie$installPart(Level level,BlockState state,BlockPos pos,ItemStack stack,CompoundTag data,CallbackInfo ci) {
        if(willow.train.hexie.compat.create.PartSchematicPlacement.place(level,pos,state,stack,data))ci.cancel();
    }
}
