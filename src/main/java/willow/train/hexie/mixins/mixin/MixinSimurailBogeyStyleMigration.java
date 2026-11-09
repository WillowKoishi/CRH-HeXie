package willow.train.hexie.mixins.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.bogey.BogeyStyleMigration;

/** BogeyType resolves its style/size block in its constructor, so migrate before it is created. */
@Pseudo
@Mixin(targets = "com.crystaelix.simurail.api.bogey.BogeyType", remap = false)
public abstract class MixinSimurailBogeyStyleMigration {
    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;)Lcom/crystaelix/simurail/api/bogey/BogeyType;",
            at = @At("HEAD"), remap = false, require = 1)
    private static void hexie$migrateSteamStyle(CompoundTag tag, CallbackInfoReturnable<?> callback) {
        BogeyStyleMigration.normalizeData(tag, "style", ResourceLocation.tryParse(tag.getString("size")));
    }
}
