package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.bogey.BogeyStyleMigration;

/** Covers saved trains and their network copies, which do not load a bogey block entity. */
@Mixin(value = CarriageBogey.class, remap = false)
public abstract class MixinCarriageBogeyStyleMigration {
    @Shadow AbstractBogeyBlock<?> type;
    @Shadow public CompoundTag bogeyData;

    @Inject(method = "<init>(Lcom/simibubi/create/content/trains/bogey/AbstractBogeyBlock;ZLnet/minecraft/nbt/CompoundTag;Lcom/simibubi/create/content/trains/entity/TravellingPoint;Lcom/simibubi/create/content/trains/entity/TravellingPoint;)V",
            at = @At("RETURN"), require = 1)
    private void hexie$migrateSteamStyle(CallbackInfo callback) {
        BogeyStyleMigration.normalizeData(bogeyData, "BogeyStyle", type.getSize().id());
    }
}
