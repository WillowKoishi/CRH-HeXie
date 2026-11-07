package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.CarriageChunkAccess;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Record resolved walking, gravity and collision displacement in the owned local frame. */
@Mixin(Entity.class)
public abstract class MixinCarriagePlayerPosition {
    @WrapMethod(method = "setPosRaw")
    private void hexie$localDisplacement(double x, double y, double z, Operation<Void> original) {
        Entity entity = (Entity) (Object) this;
        Level previous = CarriageChunkAccess.enter(entity);
        try {
            var before = entity.position();
            original.call(x, y, z);
            if (entity instanceof Player player && player.level().isClientSide && player.isLocalPlayer())
                CarriagePlayerSupport.moved(player, player.position().subtract(before));
        } finally {
            CarriageChunkAccess.exit(previous);
        }
    }

    @WrapOperation(method = "setPosRaw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk hexie$moveWithoutLoadingTerrain(Level level, int x, int z, Operation<LevelChunk> original) {
        // NeoForge's trailing chunk fetch only ensures loading; its return value is unused.
        if ((CarriageChunkAccess.reading(level) || CarriageChunkAccess.independent((Entity) (Object) this))
                && level instanceof ServerLevel server)
            return server.getChunkSource().getChunkNow(x, z);
        return original.call(level, x, z);
    }

    @ModifyReturnValue(method = "isAlwaysTicking", at = @At("RETURN"))
    private boolean hexie$logicalCarriageVisibility(boolean original) {
        // Create still creates/discards empty carriers according to terrain visibility.
        // A carrier that exists keeps its identity while its logical train moves sections.
        return original || (Object) this instanceof SnapshotCarriageEntity;
    }

    @WrapMethod(method = "move")
    private void hexie$moveOnAvailableTerrain(MoverType type, Vec3 movement, Operation<Void> original) {
        Level previous = CarriageChunkAccess.enter((Entity) (Object) this);
        try { original.call(type, movement); }
        finally { CarriageChunkAccess.exit(previous); }
    }

    @WrapMethod(method = "baseTick")
    private void hexie$environmentOnAvailableTerrain(Operation<Void> original) {
        Level previous = CarriageChunkAccess.enter((Entity) (Object) this);
        try { original.call(); }
        finally { CarriageChunkAccess.exit(previous); }
    }
}
