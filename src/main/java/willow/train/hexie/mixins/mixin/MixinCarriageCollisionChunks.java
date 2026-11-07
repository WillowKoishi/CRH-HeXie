package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriageChunkAccess;

@Mixin(BlockCollisions.class)
public abstract class MixinCarriageCollisionChunks {
    @WrapOperation(method = "getChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/CollisionGetter;getChunkForCollisions(II)Lnet/minecraft/world/level/BlockGetter;"))
    private BlockGetter hexie$loadedCollisions(CollisionGetter world, int x, int z, Operation<BlockGetter> original) {
        if (world instanceof ServerLevel server && CarriageChunkAccess.reading(server))
            return server.getChunkSource().getChunkNow(x, z);
        return original.call(world, x, z);
    }
}
