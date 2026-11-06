package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.TrainHUD;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

@Mixin(TrainHUD.class)
public abstract class MixinTrainHudDirection {
    @WrapOperation(method = "renderOverlay", at = @At(value = "FIELD",
            target = "Lcom/simibubi/create/content/trains/entity/CarriageContraptionEntity;yaw:F"),
            remap = false, require = 1, allow = 1)
    private static float hexie$directionYaw(CarriageContraptionEntity carriage, Operation<Float> original,
            GuiGraphics graphics, DeltaTracker ticks) {
        if (!(carriage instanceof SnapshotCarriageEntity snapshot)) return original.call(carriage);
        // Create adds 90 degrees for controls facing this way. W points away from the operator.
        var controlsFacing = carriage.getInitialOrientation().getCounterClockWise();
        Vec3 forward = snapshot.applyRotation(Vec3.atLowerCornerOf(controlsFacing.getOpposite().getNormal()),
                ticks.getGameTimeDeltaPartialTick(false));
        return (float) Math.toDegrees(Math.atan2(-forward.x, forward.z)) - 90f;
    }
}
