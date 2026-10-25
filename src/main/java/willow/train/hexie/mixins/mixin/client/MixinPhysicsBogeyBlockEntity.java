package willow.train.hexie.mixins.mixin.client;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.Main;
import willow.train.hexie.client.render.bogey.DF11GBogeyRenderer.QJBogeyRenderer;

/** Supplies per-entity animation data for both Simurail rendering backends. */
@Pseudo
@Mixin(targets = "com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity", remap = false)
public abstract class MixinPhysicsBogeyBlockEntity {
    @Shadow
    public double visualSpeed;

    @Inject(method = "getBogeyData", at = @At("RETURN"), remap = false, require = 1)
    private void hexie$updateQJAnimationData(CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag data = callback.getReturnValue();
        if (!(Main.MOD_ID + ":df11gbogey").equals(data.getString("BogeyStyle")))
            return;

        // This tag belongs to the physical block entity; menu previews use separate tags.
        data.putBoolean(QJBogeyRenderer.PHYSICS_BOGEY_KEY, true);
        // Simurail integrates wheel angle using blocks/second. Create's QJ animation uses
        // blocks/tick with the opposite angle sign; the backward renderer reverses it once more.
        data.putFloat(QJBogeyRenderer.TRAIN_SPEED_KEY, (float) (-visualSpeed / 20.0));
    }
}
