package willow.train.hexie.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Picking uses the same interpolated body frame as the visible carriage. */
public final class CarriageInteractionCoordinates {
    private CarriageInteractionCoordinates() {}

    public static float partialTick() {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        return camera.getEntity() == null ? minecraft.getTimer().getGameTimeDeltaPartialTick(true)
                : camera.getPartialTickTime();
    }

    public static net.minecraft.world.phys.BlockHitResult pick(net.minecraft.world.entity.player.Player player,
            SnapshotCarriageEntity car) {
        if (!(player instanceof net.minecraft.client.player.LocalPlayer local)) return null;
        var ray = com.simibubi.create.content.contraptions.ContraptionHandlerClient.getRayInputs(local);
        return com.simibubi.create.content.contraptions.ContraptionHandlerClient.rayTraceContraption(
                ray.getFirst(), ray.getSecond(), car);
    }

    public static AbstractContraptionEntity compatibilityEntity(SnapshotCarriageEntity car) {
        var carriage = car.getCarriage();
        if (carriage != null) {
            var dimensional = carriage.getDimensionalIfPresent(car.level().dimension());
            if (dimensional != null && dimensional.entity != null) {
                var entity = dimensional.entity.get();
                if (entity != null && !entity.isRemoved()) return entity;
            }
        }
        return car;
    }

    public static Vec3 toCompatibility(SnapshotCarriageEntity visible,
            AbstractContraptionEntity compatibility, Vec3 world) {
        Vec3 local = CarriagePlayerSupport.frame(visible, partialTick()).local(world);
        return compatibility.toGlobalVector(local, 1);
    }

    public static Vec3 toWorld(AbstractContraptionEntity entity, Vec3 local) {
        return entity instanceof SnapshotCarriageEntity car
                ? CarriagePlayerSupport.frame(car, partialTick()).world(local)
                : entity.toGlobalVector(local, 1);
    }
}
