package willow.train.hexie.blocks.coupler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blockentity.CouplerBlockEntity;

/** Model-space picking shared by placed blocks and backend-specific vehicle picking. */
public final class CouplerInteraction {
    private static final Vec3 CENTER = new Vec3(.5, .5, .5);

    private CouplerInteraction() {}

    public static CouplerGeometry.Hit classifyWorld(Level level, BlockPos position,
            BlockState state, Player player) {
        float progress = level.getBlockEntity(position) instanceof CouplerBlockEntity coupler
                ? coupler.coverProgress(1) : coverProgress(state);
        Vec3 from = player.getEyePosition();
        return clipWorld(state, position, progress, from,
                from.add(player.getLookAngle().scale(player.blockInteractionRange())));
    }

    public static CouplerGeometry.Hit classifyWorld(Level level, BlockPos position,
            BlockState state, Player player, BlockHitResult requested) {
        float progress = level.getBlockEntity(position) instanceof CouplerBlockEntity coupler
                ? coupler.coverProgress(1) : coverProgress(state);
        Vec3 from = player.getEyePosition(), direction = requested.getLocation().subtract(from);
        if (direction.lengthSqr() < 1e-12) return null;
        // A cover can have moved after the client click. Do not continue through its old
        // surface and turn that cover action into an interaction with the head behind it.
        return clipWorld(state, position, progress, from,
                requested.getLocation().add(direction.normalize().scale(1e-4)));
    }

    /** Extends only vanilla's owner-cell coordinate check; reach and intervening blocks still apply. */
    public static boolean validExtendedHit(ServerPlayer player, BlockHitResult requested) {
        var level = player.serverLevel();
        BlockPos position = requested.getBlockPos();
        var state = level.getBlockState(position);
        if (!hasGeometry(state)
                || !(level.getBlockEntity(position) instanceof CouplerBlockEntity coupler)) return false;
        Vec3 origin = player.getEyePosition(), point = requested.getLocation();
        Vec3 direction = point.subtract(origin);
        double reach = player.blockInteractionRange();
        if (direction.lengthSqr() < 1e-12 || direction.lengthSqr() > reach * reach) return false;
        var actual = clipWorld(state, position, coupler.coverProgress(1), origin,
                point.add(direction.normalize().scale(1e-4)));
        if (actual == null || actual.point().distanceToSqr(point) > 1e-6) return false;
        var obstruction = level.clip(new ClipContext(origin, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS || obstruction.getBlockPos().equals(position)
                || obstruction.getLocation().distanceToSqr(origin) + 1e-6 >= point.distanceToSqr(origin);
    }

    /** Returned point and face are in the same coordinate system as from/to. */
    public static CouplerGeometry.Hit clipWorld(BlockState state, BlockPos position,
            float progress, Vec3 from, Vec3 to) {
        var hook = CouplerBlock.of(state);
        if (hook == null || hook.geometry() == null) return null;
        var hit = hook.geometry().clip(progress,
                toModel(state, position, from), toModel(state, position, to));
        return fromModel(state, position, hit);
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
                Direction.getNearest((float) normal.x, (float) normal.y, (float) normal.z), hit.head());
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
