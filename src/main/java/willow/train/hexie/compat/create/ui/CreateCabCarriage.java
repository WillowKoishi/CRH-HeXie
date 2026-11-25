package willow.train.hexie.compat.create.ui;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.ui.client.ClientCarriageUiBridge.NativeTrain;

/** Native train readings shared by the console ribbon and Create's shunting instruments. */
record CreateCabCarriage(Train train, boolean snapshot) implements NativeTrain {
    static CreateCabCarriage of(Entity vehicle) {
        if (!(vehicle instanceof CarriageContraptionEntity car) || car.getCarriage() == null || car.getCarriage().train == null) return null;
        return new CreateCabCarriage(car.getCarriage().train, vehicle instanceof SnapshotCarriageView);
    }

    @Override public double speed() { return train.speed; }

    @Override public double throttle() { return train.throttle; }

    @Override public float manualLimit() {
        return train.maxSpeed() * AllConfigs.server().trains.manualTrainSpeedModifier.getF();
    }

    static boolean nativeControlsActive() {
        return ControlsHandler.getContraption() instanceof CarriageContraptionEntity car
                && car.getCarriage() != null && ControlsHandler.getControlsPos() != null;
    }

    static Vec3 rotate(Entity vehicle, Vec3 direction, float partialTick) {
        return vehicle instanceof AbstractContraptionEntity carrier ? carrier.applyRotation(direction, partialTick) : direction;
    }
}
