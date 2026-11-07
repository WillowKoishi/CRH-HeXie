package willow.train.hexie.blocks.coupler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blockentity.CouplerBlockEntity;
import willow.train.hexie.compat.sable.SableInteractionCoordinates;

/** Model-space picking shared by placed blocks and backend-specific vehicle picking. */
public final class CouplerInteraction {
    private static final Vec3 CENTER = new Vec3(.5, .5, .5);

    private CouplerInteraction() {}

    public static CouplerGeometry.Hit classifyHit(Level level, BlockPos position,
            BlockState state, BlockHitResult requested) {
        CouplerBlockEntity coupler = level.getBlockEntity(position) instanceof CouplerBlockEntity entity ? entity : null;
        float progress = coupler != null ? coupler.coverProgress(1) : coverProgress(state);
        Vec3 point = requested.getLocation();
        Vec3 offset = Vec3.atLowerCornerOf(requested.getDirection().getNormal()).scale(1e-4);
        // Classify the native surface in its own coordinate system. If a cover has
        // moved since the click, do not extend the ray to the head behind it.
        var hit = clipWorld(state, position, progress, coupler, 1, point.add(offset), point.subtract(offset));
        return hit != null && hit.point().distanceToSqr(point) <= 1e-8 ? hit : null;
    }

    /** Extends only vanilla's owner-cell coordinate check; reach and intervening blocks still apply. */
    public static boolean validExtendedHit(ServerPlayer player, BlockHitResult requested) {
        var level = player.serverLevel();
        BlockPos position = requested.getBlockPos();
        var state = level.getBlockState(position);
        if (!hasGeometry(state)
                || !(level.getBlockEntity(position) instanceof CouplerBlockEntity coupler)) return false;
        var frame = SableInteractionCoordinates.logical(level, position);
        Vec3 worldEye = SableInteractionCoordinates.logicalWorld(level, player.getEyePosition());
        Vec3 origin = frame.toStorage(worldEye), point = requested.getLocation();
        Vec3 direction = point.subtract(origin);
        double distanceSquared = worldEye.distanceToSqr(frame.toWorld(point));
        double reach = player.blockInteractionRange();
        if (direction.lengthSqr() < 1e-12 || distanceSquared > reach * reach) return false;
        var actual = clipWorld(state, position, coupler.coverProgress(1), coupler, 1, origin,
                point.add(direction.normalize().scale(1e-4)));
        if (actual == null || actual.point().distanceToSqr(point) > 1e-6) return false;
        var obstruction = level.clip(new ClipContext(origin, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS || obstruction.getBlockPos().equals(position)
                || SableInteractionCoordinates.logicalDistanceSquared(level, worldEye,
                        obstruction.getBlockPos(), obstruction.getLocation()) + 1e-6 >= distanceSquared;
    }

    /** Returned point and face are in the same coordinate system as from/to. */
    public static CouplerGeometry.Hit clipWorld(BlockState state, BlockPos position,
            float progress, Vec3 from, Vec3 to) {
        return clipWorld(state, position, progress, null, 1, from, to);
    }

    public static CouplerGeometry.Hit clipWorld(BlockState state, BlockPos position,
            float progress, CouplerBlockEntity entity, float partial, Vec3 from, Vec3 to) {
        var hook = CouplerBlock.of(state);
        if (hook == null || hook.geometry() == null) return null;
        Vec3 a = toModel(state, position, from), b = toModel(state, position, to);
        var hit = hook.geometry().clip(progress, a, b);
        if (entity != null && entity.adapter() != null) {
            var complete = hook.complete();
            Vec3 contact = new Vec3(.5 + complete.x(), .5 + complete.y(), .5 + complete.z());
            var adapter = CouplerAdapterGeometry.clip(entity.adapter(), hook.model(), entity.rescueKnuckleProgress(partial),
                    a.subtract(contact), b.subtract(contact));
            if (adapter != null) {
                adapter = new CouplerGeometry.Hit(adapter.point().add(contact), adapter.face(), true, adapter.adapterIndex());
                hit = nearer(a, hit, adapter);
            }
        }
        return fromModel(state, position, hit);
    }

    public static CouplerGeometry.Hit nearer(Vec3 origin, CouplerGeometry.Hit first, CouplerGeometry.Hit second) {
        return second == null || first != null && first.point().distanceToSqr(origin) <= second.point().distanceToSqr(origin) ? first : second;
    }

    public static Vec3 toModel(BlockState state, BlockPos position, Vec3 point) {
        return rotate(point.subtract(Vec3.atLowerCornerOf(position)).subtract(CENTER),
                CouplerBlock.of(state).facing(state), true).add(CENTER);
    }

    public static Vec3 fromModel(BlockState state, BlockPos position, Vec3 point) {
        return rotate(point.subtract(CENTER), CouplerBlock.of(state).facing(state), false)
                .add(CENTER).add(Vec3.atLowerCornerOf(position));
    }

    public static CouplerGeometry.Hit fromModel(BlockState state, BlockPos position,
            CouplerGeometry.Hit hit) {
        if (hit == null) return null;
        Vec3 normal = rotate(Vec3.atLowerCornerOf(hit.face().getNormal()),
                CouplerBlock.of(state).facing(state), false);
        return new CouplerGeometry.Hit(fromModel(state, position, hit.point()),
                Direction.getNearest((float) normal.x, (float) normal.y, (float) normal.z), hit.head(), hit.adapterIndex());
    }

    public static boolean hasGeometry(BlockState state) {
        var hook = CouplerBlock.of(state);
        return hook != null && hook.geometry() != null;
    }

    public static float coverProgress(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN) ? 1 : 0;
    }

    private static Vec3 rotate(Vec3 point, Direction facing, boolean inverse) {
        if (inverse) facing = switch (facing) {
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
            default -> facing;
        };
        return switch (facing) {
            case EAST -> new Vec3(-point.z, point.y, point.x);
            case SOUTH -> new Vec3(-point.x, point.y, -point.z);
            case WEST -> new Vec3(point.z, point.y, -point.x);
            default -> point;
        };
    }
}
