package willow.train.hexie.bridge.sync;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.sync.VehicleSyncData.CarriageContent;
import willow.train.hexie.bridge.sync.VehicleSyncData.Membership;
import willow.train.hexie.core.api.vehicle.Point3;
import willow.train.hexie.bridge.sync.RegionData.RegionKey;
import willow.train.hexie.bridge.sync.RegionData.RegionSnapshot;
import willow.train.hexie.core.api.interaction.BlockControlHit;

public interface VehicleCacheView {
    record Pose(Point3 position, Rotation rotation) {}

    record Rotation(double x, double y, double z, double w) {
        public Rotation {
            double length = Math.hypot(Math.hypot(x, y), Math.hypot(z, w));
            if (!(length > 0) || !Double.isFinite(length)) throw new IllegalArgumentException("Invalid rotation");
            x /= length;
            y /= length;
            z /= length;
            w /= length;
        }

        public double dot(Rotation other) { return x * other.x + y * other.y + z * other.z + w * other.w; }

        public Rotation slerp(Rotation other, double t) {
            double dot = dot(other);
            double cosine = Math.min(1, Math.abs(dot));
            double from = 1 - t, to = t;
            if (cosine < .9995) {
                double angle = Math.acos(cosine);
                double sin = Math.sin(angle);
                from = Math.sin(from * angle) / sin;
                to = Math.sin(to * angle) / sin;
            }
            if (dot < 0) to = -to;
            return new Rotation(from * x + to * other.x, from * y + to * other.y,
                    from * z + to * other.z, from * w + to * other.w);
        }
    }

    record RenderCell(BlockPos position, BlockState state) {}

    /** Allows a carrier's surface/interaction index to follow accepted content and pose changes. */
    interface Host {
        void bind(UUID id, Pose pose, CarriageContent body);
        void move(UUID id, Pose pose);
        void unbind(UUID id);
    }

    Map<UUID, ? extends Membership> membership();
    Optional<? extends CarriageContent> body(UUID carriageId);
    /** Velocities are world blocks per tick. Binding starts a fresh interpolation window. */
    void bind(UUID carriageId, long tick, Pose pose, Point3 velocity);
    void bind(UUID carriageId, long tick, Pose pose, Point3 velocity, Host host);
    void move(UUID carriageId, long tick, Pose pose, Point3 velocity);
    void unbind(UUID carriageId);
    Set<UUID> boundCarriages();
    Optional<Pose> poseAt(UUID carriageId, float partialTick);
    Optional<RegionSnapshot> region(RegionKey key);
    List<RenderCell> renderCells(UUID carriageId);
    void registerMountedModels(BiFunction<UUID, List<RenderCell>, List<RenderCell>> adapter);
    Optional<BlockGetter> blockView(Level level, UUID carriageId);
    Optional<BlockControlHit> pickControl(Entity carrier, BlockPos localCell, Vec3 localFrom, Vec3 localTo,
                                          float partialTick);
}
