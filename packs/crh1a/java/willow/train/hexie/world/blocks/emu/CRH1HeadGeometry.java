package willow.train.hexie.world.blocks.emu;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlHit;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.collision.EmuCollision;

/** Model-space transforms and picking, exported from the authored CRH1A head. */
public final class CRH1HeadGeometry {
    public static final Vec3 AXIS, UPPER_PIVOT, UPPER_DELTA, LOWER_DELTA;
    public static final float UPPER_ANGLE;
    public static final double CONTACT_HEIGHT = (32 - 22.4) / 16;
    public static final List<CRH1HeadGeometry.Triangle> UPPER, LOWER, FRONT;

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
        } catch (IOException error) { throw new ExceptionInInitializerError(error); }
        EmuCollision.ForCRH1HeadGeometry.initialize();
    }

    private CRH1HeadGeometry() {}

    public static Vec3 armOrigin() { return new Vec3(AXIS.x, CONTACT_HEIGHT, AXIS.z); }
    public static AABB controlBounds(BlockState state) {
        return EmuBlockInteract.ForCRH1HeadGeometry.controlBounds(state);
    }
    public static List<AABB> playerBoxes(Direction facing) {
        return EmuCollision.ForCRH1HeadGeometry.playerBoxes(facing);
    }
    public static AABB bounds(Direction facing) {
        return rotateBox(new AABB(-1.4, -.1, -3.1, 2.4, 4, 1), facing);
    }
    public static AABB rotateBox(AABB box, Direction facing) {
        return EmuCollision.ForCRH1HeadGeometry.rotateBox(box, facing);
    }
    public static List<VoxelShape> collisions(BlockGetter world, AABB query) {
        return EmuCollision.ForCRH1HeadGeometry.collisions(world, query);
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
        return EmuBlockInteract.ForCRH1HeadGeometry.pick(target, from, to);
    }

    public static CRH1HeadGeometry.Hit clip(List<CRH1HeadGeometry.Triangle> triangles, Vec3 from, Vec3 to, UnaryOperator<Vec3> transform, boolean head) {
        return EmuBlockInteract.ForCRH1HeadGeometry.clip(triangles, from, to, transform, head);
    }

    public static CRH1HeadGeometry.Hit nearer(Vec3 from, CRH1HeadGeometry.Hit a, CRH1HeadGeometry.Hit b) {
        return EmuBlockInteract.ForCRH1HeadGeometry.nearer(from, a, b);
    }
    public static Vec3 rotateFacing(Vec3 normal, Direction facing) {
        return EmuBlockInteract.ForCRH1HeadGeometry.rotateFacing(normal, facing);
    }
    public static Vec3 rotate(Quaternionf rotation, Vec3 point) {
        var result = rotation.transform(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3(result.x, result.y, result.z);
    }
    private static Vec3 vector(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }
    private static List<CRH1HeadGeometry.Triangle> triangles(JsonArray entries) {
        var result = new ArrayList<CRH1HeadGeometry.Triangle>();
        for (var entry : entries) {
            var t = entry.getAsJsonArray();
            result.add(new CRH1HeadGeometry.Triangle(vector(t.get(0).getAsJsonArray()), vector(t.get(1).getAsJsonArray()),
                    vector(t.get(2).getAsJsonArray())));
        }
        return List.copyOf(result);
    }
    public record Triangle(Vec3 a, Vec3 b, Vec3 c) {}
    public record Hit(Vec3 point, Vec3 normal, boolean head) {}
}
