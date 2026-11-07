package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.SnapshotBodyAnchor;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.compat.create.CarriagePlayerSupport;

@Mixin(value = Carriage.class, remap = false)
public abstract class MixinSnapshotCarriageLoading {
    @WrapOperation(method = "manageEntities", at = @At(value = "FIELD",
            target = "Lcom/simibubi/create/content/trains/entity/Carriage$DimensionalCarriageEntity;positionAnchor:Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 hexie$bodyChunk(Carriage.DimensionalCarriageEntity carriage, Operation<Vec3> original) {
        return ((SnapshotBodyAnchor) carriage).hexie$bodyPosition(original.call(carriage));
    }

    @WrapOperation(method = "manageEntities", at = @At(value = "FIELD",
            target = "Lcom/simibubi/create/content/trains/entity/CarriageContraptionEntity;leftTickingChunks:Z"))
    private boolean hexie$retainOccupiedBody(CarriageContraptionEntity entity, Operation<Boolean> original) {
        boolean left = original.call(entity);
        if (left && entity instanceof SnapshotCarriageEntity car && CarriagePlayerSupport.hasSupportedPlayers(car)) {
            // Keep the floor and its occupants. Empty carriers still use Create's unload/save path.
            entity.leftTickingChunks = false;
            return false;
        }
        return left;
    }
}
