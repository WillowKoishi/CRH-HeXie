package willow.train.hexie.compat.mixins.client.simurail;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.bridge.render.BogeyRenderBridge.DF11GBogeyRendererQJBogeyRendererOperations;
import willow.train.hexie.core.HexieConstants;

/** Supplies per-entity animation data for both Simurail rendering backends. */
@Pseudo
@Mixin(targets = "com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity", remap = false)
public abstract class MixinPhysicsBogeyBlockEntity {
    @Shadow
    public double visualSpeed;

    @Inject(method = "getBogeyData", at = @At("RETURN"), remap = false, require = 1)
    private void hexie$updateQJAnimationData(CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag data = callback.getReturnValue();
        String style = data.getString("BogeyStyle");
        if (!(HexieConstants.MOD_ID + ":loco").equals(style)
                && !(HexieConstants.MOD_ID + ":steambogey").equals(style))
            return;

        // This tag belongs to the physical block entity; menu previews use separate tags.
        data.putBoolean(DF11GBogeyRendererQJBogeyRendererOperations.PHYSICS_BOGEY_KEY, true);
        // Simurail integrates wheel angle using blocks/second. Create's QJ animation uses
        // blocks/tick with the opposite angle sign; the backward renderer reverses it once more.
        data.putFloat(DF11GBogeyRendererQJBogeyRendererOperations.TRAIN_SPEED_KEY, (float) (-visualSpeed / 20.0));
    }
}
