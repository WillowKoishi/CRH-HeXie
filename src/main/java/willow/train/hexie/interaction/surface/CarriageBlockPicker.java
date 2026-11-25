package willow.train.hexie.interaction.surface;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.bridge.interaction.SurfaceInputBridge.SurfaceInputSourcesRayView;
import willow.train.hexie.interaction.spatial.model.Pose;
import willow.train.hexie.interaction.spatial.model.Vec;

/** Includes overhanging outlines and unregistered obstructions in the same distance comparison. */
public final class CarriageBlockPicker {
    public record Body(BlockGetter blocks, Iterable<BlockPos> positions) {}
    public record Hit(UUID carriage, BlockPos owner) {}

    /** Direction is unit length. Reach is the remaining world distance after clipping terrain. */
    public static Optional<Hit> pick(Map<UUID, Pose> poses, Function<UUID, Optional<Body>> bodies,
            Predicate<BlockState> interactive, Vec eye, Vec direction, double reach) {
        Hit selected = null;
        var worldRay = new SurfaceRay(eye, direction);
        for (var entry : poses.entrySet()) {
            var body = bodies.apply(entry.getKey());
            if (body.isEmpty()) continue;
            var local = worldRay.local(entry.getValue());
            var start = SurfaceRay.mc(local.origin());
            var end = start.add(SurfaceRay.mc(local.direction()).scale(reach));
            var blocks = body.get().blocks();
            var hit = pick(blocks, body.get().positions(), start, end, reach);
            if (hit == null) continue;
            reach = start.distanceTo(hit.getLocation());
            var owner = hit.getBlockPos();
            selected = interactive.test(blocks.getBlockState(owner)) ? new Hit(entry.getKey(), owner) : null;
        }
        return Optional.ofNullable(selected);
    }

    public static BlockHitResult pick(BlockGetter blocks, Iterable<BlockPos> owners, SurfaceInputSourcesRayView ray) {
        return pick(blocks, owners, ray.from(), ray.to(), ray.from().distanceTo(ray.to()));
    }

    private static BlockHitResult pick(BlockGetter blocks, Iterable<BlockPos> owners,
            Vec3 from, Vec3 to, double reach) {
        BlockHitResult selected = null;
        for (var owner : owners) {
            var state = blocks.getBlockState(owner);
            if (state.isAir()) continue;
            var hit = blocks.clipWithInteractionOverride(from, to, owner.immutable(), state.getShape(blocks, owner), state);
            if (hit == null) continue;
            double distance = from.distanceTo(hit.getLocation());
            if (distance < reach) {
                reach = distance;
                selected = hit;
            }
        }
        return selected;
    }

    private CarriageBlockPicker() {}
}
