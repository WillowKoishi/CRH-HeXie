package willow.train.hexie.blocks.emu.crh1a;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlHit;
import willow.train.hexie.blockentity.CRH1HeadEntity;
import willow.train.hexie.blocks.coupler.CouplerInteraction;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.interaction.NativeControlSurface;

/** Model-space transforms and picking, exported from the authored CRH1A head. */
public final class CRH1HeadGeometry {
    public static final Vec3 AXIS, UPPER_PIVOT, UPPER_DELTA, LOWER_DELTA;
    public static final float UPPER_ANGLE;
    public static final double CONTACT_HEIGHT = (32 - 22.4) / 16;
    private static final List<Triangle> UPPER, LOWER, FRONT;
    private static final Map<Direction, List<AABB>> PLAYER_BOXES = new EnumMap<>(Direction.class);
    private static final AABB CONTROLS = new AABB(-.4, -.1, -3.1, 1.4, 2, -1.35);

    static {
        try (var input = CRH1HeadGeometry.class.getResourceAsStream("/data/hexie/crh1a_head_geometry.json")) {
            if (input == null) throw new IllegalStateException("Missing CRH1A head geometry");
            var data = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            AXIS = vector(data.getAsJsonArray("axis"));
            UPPER_PIVOT = vector(data.getAsJsonArray("upper_pivot"));
            UPPER_DELTA = vector(data.getAsJsonArray("upper_delta"));
            LOWER_DELTA = vector(data.getAsJsonArray("lower_delta"));
            UPPER_ANGLE = data.get("upper_angle").getAsFloat();
            UPPER = triangles(data.getAsJsonArray("cover_upper"));
            LOWER = triangles(data.getAsJsonArray("cover_lower"));
            FRONT = triangles(data.getAsJsonArray("coupler_front"));
        } catch (java.io.IOException error) { throw new ExceptionInInitializerError(error); }
        // Coarse shell slabs, leaving the interior and the rear open.
        var north = List.of(new AABB(-1.14, 1, -1.5, -.93, 3.2, 1),
                new AABB(1.93, 1, -1.5, 2.14, 3.2, 1),
                new AABB(-1.05, 3.2, -1.65, 2.05, 3.75, 1),
                new AABB(-.8, 1, -2.15, 1.8, 2.6, -2.0));
        for (var facing : Direction.Plane.HORIZONTAL)
            PLAYER_BOXES.put(facing, north.stream().map(box -> rotateBox(box, facing)).toList());
    }

    private CRH1HeadGeometry() {}

    public static Vec3 armOrigin() { return new Vec3(AXIS.x, CONTACT_HEIGHT, AXIS.z); }
    public static AABB controlBounds(BlockState state) {
        return rotateBox(CONTROLS, state.getValue(CRH1HeadBlock.FACING));
    }
    public static List<AABB> playerBoxes(Direction facing) { return PLAYER_BOXES.get(facing); }
    public static AABB bounds(Direction facing) {
        return rotateBox(new AABB(-1.4, -.1, -3.1, 2.4, 4, 1), facing);
    }
    private static AABB rotateBox(AABB box, Direction facing) {
        Vec3 a = rotateFacing(new Vec3(box.minX - .5, box.minY, box.minZ - .5), facing).add(.5, 0, .5);
        Vec3 b = rotateFacing(new Vec3(box.maxX - .5, box.maxY, box.maxZ - .5), facing).add(.5, 0, .5);
        return new AABB(a, b);
    }
    public static List<net.minecraft.world.phys.shapes.VoxelShape> collisions(BlockGetter world, AABB query) {
        var roots = new java.util.HashSet<net.minecraft.core.BlockPos>();
        var min = new net.minecraft.core.BlockPos((int) Math.floor(query.minX) - 3,
                (int) Math.floor(query.minY) - 4, (int) Math.floor(query.minZ) - 3);
        var max = new net.minecraft.core.BlockPos((int) Math.floor(query.maxX) + 3,
                (int) Math.floor(query.maxY) + 1, (int) Math.floor(query.maxZ) + 3);
        if (world instanceof net.minecraft.world.level.CollisionGetter chunks) {
            for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++)
                for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                    var source = chunks.getChunkForCollisions(cx, cz);
                    if (!(source instanceof net.minecraft.world.level.chunk.ChunkAccess chunk)) continue;
                    for (int sy = min.getY() >> 4; sy <= max.getY() >> 4; sy++) {
                        int index = chunk.getSectionIndex(sy * 16);
                        if (index < 0 || index >= chunk.getSections().length) continue;
                        var section = chunk.getSections()[index];
                        if (section.hasOnlyAir() || !section.maybeHas(state ->
                                state.getBlock() instanceof CRH1HeadBlock || state.getBlock() instanceof CRH1HeadFloorBlock)) continue;
                        var low = new net.minecraft.core.BlockPos(Math.max(min.getX(), cx * 16),
                                Math.max(min.getY(), sy * 16), Math.max(min.getZ(), cz * 16));
                        var high = new net.minecraft.core.BlockPos(Math.min(max.getX(), cx * 16 + 15),
                                Math.min(max.getY(), sy * 16 + 15), Math.min(max.getZ(), cz * 16 + 15));
                        for (var pos : net.minecraft.core.BlockPos.betweenClosed(low, high)) {
                            var state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
                            if (state.getBlock() instanceof CRH1HeadBlock) roots.add(pos.immutable());
                            else if (state.getBlock() instanceof CRH1HeadFloorBlock floor)
                                roots.add(floor.partRoot(world, pos, state).immutable());
                        }
                    }
                }
        }
        var result = new ArrayList<net.minecraft.world.phys.shapes.VoxelShape>();
        for (var root : roots) {
            if (world instanceof net.minecraft.world.level.Level level && !level.hasChunkAt(root)) continue;
            var state = world.getBlockState(root);
            if (!(state.getBlock() instanceof CRH1HeadBlock)) continue;
            for (var local : playerBoxes(state.getValue(CRH1HeadBlock.FACING))) {
                var box = local.move(root);
                if (box.intersects(query)) result.add(net.minecraft.world.phys.shapes.Shapes.create(box));
            }
        }
        return result;
    }

    public static Vec3 lower(Vec3 point, float progress) {
        float open = CRH1HeadAnimation.open(progress);
        float back = CRH1HeadAnimation.withdraw(progress);
        return point.add(LOWER_DELTA.x * open, LOWER_DELTA.y * open,
                back / 16d + (LOWER_DELTA.z - 1 / 16d) * open);
    }

    public static Vec3 upper(Vec3 point, float progress) {
        float open = CRH1HeadAnimation.open(progress);
        float back = CRH1HeadAnimation.withdraw(progress);
        double angle = Math.toRadians(UPPER_ANGLE * open);
        Vec3 relative = point.subtract(UPPER_PIVOT);
        double c = Math.cos(angle), s = Math.sin(angle);
        return new Vec3(relative.x, relative.y * c - relative.z * s, relative.y * s + relative.z * c)
                .add(UPPER_PIVOT).add(UPPER_DELTA.x * open, UPPER_DELTA.y * open,
                        back / 16d + (UPPER_DELTA.z - 1 / 16d) * open);
    }

    public static BlockControlHit pick(BlockControlContext target, Vec3 from, Vec3 to) {
        BlockState state = target.state();
        float progress;
        SnapshotCarriageEntity carriage = target.carrier() instanceof SnapshotCarriageEntity car ? car : null;
        if (carriage != null) {
            var info = carriage.getContraption().getBlocks().get(target.position());
            progress = info == null ? 0 : CRH1HeadAnimation.progress(info.state(), info.nbt(), target.time());
        } else if (target.blocks().getBlockEntity(target.position()) instanceof CRH1HeadEntity entity)
            progress = entity.animationProgress(target.partialTick());
        else progress = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN) ? 1 : 0;
        Vec3 a = CouplerInteraction.toModel(state, target.position(), from);
        Vec3 b = CouplerInteraction.toModel(state, target.position(), to);
        Hit upper = clip(UPPER, a, b, point -> upper(point, progress), false);
        Hit lower = clip(LOWER, a, b, point -> lower(point, progress), false);
        var body = nearer(a, upper, lower);
        if (body != null) body = new Hit(CouplerInteraction.fromModel(state, target.position(), body.point),
                rotateFacing(body.normal, state.getValue(CRH1HeadBlock.FACING)), false);

        var frame = carriage == null ? null : carriage.couplerAnimation().frameAt(target.position(), target.partialTick());
        Hit front;
        if (frame == null) {
            front = clip(FRONT, a, b, point -> point.add(0, 0, -CRH1HeadAnimation.EXTENSION
                    * CRH1HeadAnimation.extension(progress)), true);
            if (front != null) front = new Hit(CouplerInteraction.fromModel(state, target.position(), front.point),
                    rotateFacing(front.normal, state.getValue(CRH1HeadBlock.FACING)), true);
        } else {
            Vec3 pivot = new Vec3(frame.x(), frame.y(), frame.z());
            Quaternionf rotation = frame.rotation(), inverse = new Quaternionf(rotation).conjugate();
            Vec3 af = rotate(inverse, from.subtract(pivot)).add(armOrigin());
            Vec3 bf = rotate(inverse, to.subtract(pivot)).add(armOrigin());
            front = clip(FRONT, af, bf, point -> point.add(0, 0, -CRH1HeadAnimation.EXTENSION
                    * CRH1HeadAnimation.extension(progress)), true);
            if (front != null) front = new Hit(rotate(rotation, front.point.subtract(armOrigin())).add(pivot),
                    rotate(rotation, front.normal), true);
        }
        Hit hit = nearer(from, body, front);
        if (hit == null) return null;
        Direction face = Direction.getNearest((float) hit.normal.x, (float) hit.normal.y, (float) hit.normal.z);
        return new BlockControlHit(hit.head ? NativeControlSurface.Control.COUPLER_HEAD : NativeControlSurface.Control.COUPLER_BODY,
                new BlockHitResult(hit.point, face, target.position(), false));
    }

    private static Hit clip(List<Triangle> triangles, Vec3 from, Vec3 to, UnaryOperator<Vec3> transform, boolean head) {
        Vec3 ray = to.subtract(from);
        Hit best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (var triangle : triangles) {
            Vec3 a = transform.apply(triangle.a), b = transform.apply(triangle.b), c = transform.apply(triangle.c);
            Vec3 e1 = b.subtract(a), e2 = c.subtract(a), p = ray.cross(e2);
            double determinant = e1.dot(p);
            if (Math.abs(determinant) < 1e-9) continue;
            double inverse = 1 / determinant;
            Vec3 origin = from.subtract(a);
            double u = origin.dot(p) * inverse;
            if (u < -1e-9 || u > 1 + 1e-9) continue;
            Vec3 q = origin.cross(e1);
            double v = ray.dot(q) * inverse;
            if (v < -1e-9 || u + v > 1 + 1e-9) continue;
            double t = e2.dot(q) * inverse;
            if (t < 0 || t > 1 || t >= distance) continue;
            Vec3 normal = e1.cross(e2);
            if (normal.dot(ray) > 0) normal = normal.scale(-1);
            distance = t;
            best = new Hit(from.add(ray.scale(t)), normal, head);
        }
        return best;
    }

    private static Hit nearer(Vec3 from, Hit a, Hit b) {
        return a == null ? b : b == null || a.point.distanceToSqr(from) <= b.point.distanceToSqr(from) ? a : b;
    }
    private static Vec3 rotateFacing(Vec3 normal, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(-normal.z, normal.y, normal.x);
            case SOUTH -> new Vec3(-normal.x, normal.y, -normal.z);
            case WEST -> new Vec3(normal.z, normal.y, -normal.x);
            default -> normal;
        };
    }
    private static Vec3 rotate(Quaternionf rotation, Vec3 point) {
        var result = rotation.transform(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3(result.x, result.y, result.z);
    }
    private static Vec3 vector(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }
    private static List<Triangle> triangles(JsonArray entries) {
        var result = new ArrayList<Triangle>();
        for (var entry : entries) {
            var t = entry.getAsJsonArray();
            result.add(new Triangle(vector(t.get(0).getAsJsonArray()), vector(t.get(1).getAsJsonArray()),
                    vector(t.get(2).getAsJsonArray())));
        }
        return List.copyOf(result);
    }
    private record Triangle(Vec3 a, Vec3 b, Vec3 c) {}
    private record Hit(Vec3 point, Vec3 normal, boolean head) {}
}
