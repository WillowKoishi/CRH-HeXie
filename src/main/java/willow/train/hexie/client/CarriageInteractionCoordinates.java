package willow.train.hexie.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.compat.create.CarriagePlayerSupport;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.compat.sable.SableInteractionCoordinates;

/** Picking uses the same interpolated body frame as the visible carriage. */
public final class CarriageInteractionCoordinates {
    private CarriageInteractionCoordinates() {}

    public static float partialTick() {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        return camera.getEntity() == null ? minecraft.getTimer().getGameTimeDeltaPartialTick(true)
                : camera.getPartialTickTime();
    }

    /** The standing body's render displacement, composed outside the base player's eye interpolation. */
    public static Vec3 renderEyeOffset(Player player, float partial) {
        Vec3 offset = CarriagePlayerSupport.renderOffset(player, partial);
        if (offset.lengthSqr() == 0) return Vec3.ZERO;
        var carriage = CarriagePlayerSupport.attachedCarriage(player);
        if (carriage == null) return Vec3.ZERO;
        if (!SableInteractionCoordinates.contains(carriage.level(), carriage.blockPosition())) return offset;
        // The support frame is in the carrier's storage level; only its displacement is transformed.
        var frame = SableInteractionCoordinates.render(carriage.level(), carriage.blockPosition(), partial);
        Vec3 origin = carriage.position();
        return frame.toWorld(origin.add(offset)).subtract(frame.toWorld(origin));
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
        float partial = partialTick();
        Vec3 storage = SableInteractionCoordinates.render(visible.level(), visible.blockPosition(), partial)
                .toStorage(world);
        Vec3 local = CarriagePlayerSupport.frame(visible, partial).local(storage);
        return SableInteractionCoordinates.logical(compatibility.level(), compatibility.blockPosition())
                .toWorld(compatibility.toGlobalVector(local, 1));
    }

    public record PickingRay(Vec3 from, Vec3 to, Vec3 localFrom, Vec3 localTo) {}

    /**
     * Our geometry consumes the rendered carrier-local ray. Sable's native Create
     * adapter expects a logical world ray, so project the same storage points out
     * through its logical pose. This also works when an endpoint lies outside the
     * target's plot allocation; it never relies on guessing that endpoint's plot.
     */
    public static PickingRay toCompatibilityRay(AbstractContraptionEntity visible,
            AbstractContraptionEntity compatibility, Vec3 from, Vec3 to, float partial) {
        var storage = SableInteractionCoordinates.render(visible.level(), visible.blockPosition(), partial)
                .ray(from, to);
        Vec3 localFrom, localTo, nativeFrom, nativeTo;
        if (visible instanceof SnapshotCarriageEntity car) {
            var body = CarriagePlayerSupport.frame(car, partial);
            localFrom = body.local(storage.from());
            localTo = body.local(storage.to());
            nativeFrom = compatibility.toGlobalVector(localFrom, 1);
            nativeTo = compatibility.toGlobalVector(localTo, 1);
        } else {
            nativeFrom = storage.from();
            nativeTo = storage.to();
            localFrom = compatibility.toLocalVector(nativeFrom, 1);
            localTo = compatibility.toLocalVector(nativeTo, 1);
        }
        var logical = SableInteractionCoordinates.logical(compatibility.level(), compatibility.blockPosition());
        return new PickingRay(logical.toWorld(nativeFrom), logical.toWorld(nativeTo), localFrom, localTo);
    }

    public static Vec3 toWorld(AbstractContraptionEntity entity, Vec3 local) {
        float partial = partialTick();
        Vec3 storage = entity instanceof SnapshotCarriageEntity car
                ? CarriagePlayerSupport.frame(car, partial).world(local)
                : entity.toGlobalVector(local, 1);
        return SableInteractionCoordinates.render(entity.level(), entity.blockPosition(), partial).toWorld(storage);
    }
}
