package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriageChunkAccess;

/** Existing tracked carriages keep the normal packet cadence outside ticking terrain. */
@Mixin(ChunkMap.class)
public abstract class MixinCarriageTrackingTick {
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"))
    private SectionPos hexie$trackedSection(EntityAccess tracked, Operation<SectionPos> original,
            @Share("hexie$independentTracking") LocalBooleanRef independent) {
        independent.set(tracked instanceof Entity entity && CarriageChunkAccess.independent(entity));
        return original.call(tracked);
    }

    @ModifyExpressionValue(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap$DistanceManager;inEntityTickingRange(J)Z"))
    private boolean hexie$sendLogicalMovement(boolean ticking,
            @Share("hexie$independentTracking") LocalBooleanRef independent) {
        return ticking || independent.get();
    }
}
