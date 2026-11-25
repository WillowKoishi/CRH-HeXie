package willow.train.hexie.sync.cache;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import willow.train.hexie.bridge.sync.VehicleCacheView.Pose;
import willow.train.hexie.bridge.sync.VehicleCacheView.Rotation;
import willow.train.hexie.core.api.vehicle.Point3;

public final class ClientVehicleEvents {
    private ClientVehicleEvents() {}

    private static Point3 point(Vec3 p) { return new Point3(p.x, p.y, p.z); }

    private static Pose pose(Vec3 p, Quaternionfc q) {
        return new Pose(point(p), new Rotation(q.x(), q.y(), q.z(), q.w()));
    }

    public static void install(ClientVehicleCache cache) {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((EntityTickEvent.Post event) -> {
            var entity = event.getEntity();
            if (!entity.level().isClientSide || !(entity instanceof SnapshotCarriageView car)) return;
            var id = entity.getUUID();
            long tick = entity.tickCount;
            var previous = car.renderPosition(0);
            var current = car.renderPosition(1);
            var velocity = point(current.subtract(previous));
            if (!cache.boundCarriages().contains(id))
                cache.bind(id, tick - 1, pose(previous, car.bodyRotation(0)), velocity);
            cache.move(id, tick, pose(current, car.bodyRotation(1)), velocity);
        });
        bus.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel().isClientSide && event.getEntity() instanceof SnapshotCarriageView)
                cache.unbind(event.getEntity().getUUID());
        });
        bus.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel().isClientSide()) cache.leaveLevel();
        });
    }
}
