package willow.train.hexie.mixins.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.vehicle.runtime.CarriageDefinition;

@Mixin(value = RollerMovementBehaviour.class, remap = false)
public abstract class MixinSnapshotRoller {
    @ModifyReceiver(method = "createHeightProfileForTracks", require = 2, allow = 2,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction$Axis;choose(III)I"))
    private Direction.Axis hexie$installedAxis(Direction.Axis axis, int x, int y, int z, MovementContext context) {
        // The saved axle still guides the carriage even if its captured block has been replaced.
        return context.contraption.entity instanceof SnapshotCarriageEntity entity ? entity.originalForward().getAxis() : axis;
    }

    @ModifyArg(method = "createHeightProfileForTracks", index = 1, require = 1, allow = 1,
            at = @At(value = "INVOKE", ordinal = 0, target =
                    "Lcom/simibubi/create/content/contraptions/actors/roller/RollerMovementBehaviour$RollerTravellingPoint;" +
                    "travel(Lcom/simibubi/create/content/trains/graph/TrackGraph;D" +
                    "Lcom/simibubi/create/content/trains/entity/TravellingPoint$ITrackSelector;)D"))
    private double hexie$fromSnapshotOrigin(double travel, @Local(argsOnly = true) MovementContext context) {
        var car = hexie$carriage(context);
        if (car == null) return travel;
        // The scout starts on the leading bogey, while localPos is relative to the captured block origin.
        // Native positive travel runs towards decreasing route mileage.
        var leading = car.referenceBogey();
        return travel + leading.offset() + leading.mount().trackPivot().z();
    }

    @ModifyExpressionValue(method = "createHeightProfileForTracks", require = 1, allow = 1,
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/actors/roller/PaveTask;" +
                    "get(Lnet/createmod/catnip/data/Couple;)F"))
    private float hexie$installedHeight(float railHeight, MovementContext context) {
        var car = hexie$carriage(context);
        return car == null ? railHeight : railHeight + (float) car.floorOffset();
    }

    @Unique
    private static CarriageDefinition hexie$carriage(MovementContext context) {
        if (!(context.contraption.entity instanceof SnapshotCarriageEntity entity)) return null;
        var motion = entity.motion();
        return motion == null ? null : motion.runtime().definition(entity.getUUID());
    }
}
