package willow.train.hexie.core.util.seat;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.core.util.BlockRotation;

public final class DriverSeatGeometry {
    private final Map<Direction, VoxelShape> outlines = new EnumMap<>(Direction.class);
    private final Map<Direction, VoxelShape> collisions = new EnumMap<>(Direction.class);
    private final Vec3 northEye;

    public static DriverSeatGeometry original() { return new DriverSeatGeometry(Bounds.original()); }
    public static DriverSeatGeometry cab() { return new DriverSeatGeometry(Bounds.cab()); }

    private DriverSeatGeometry(Bounds bounds) {
        northEye = bounds.northEye();
        for (var facing : Direction.Plane.HORIZONTAL) {
            var support = boxes(bounds.supports(), facing);
            collisions.put(facing, support);
            outlines.put(facing, Shapes.or(support, boxes(bounds.trim(), facing)));
        }
    }

    public Vec3 eye(Direction facing) { return BlockRotation.rotate(northEye, facing); }
    public VoxelShape outline(Direction facing) { return outlines.get(facing); }
    public VoxelShape collision(Direction facing) { return collisions.get(facing); }

    private record Bounds(double[][] supports, double[][] trim, double[] cushionTop) {
        Vec3 northEye() { return new Vec3(cushionTop[0], cushionTop[1] + 14.0 / 16, cushionTop[2]); }

        /** North-facing bounds of the original centred CRH2A chair. */
        static Bounds original() {
            return new Bounds(new double[][] {
                    {.5, .375, .3125, .625, .625, .75},
                    {.375, .375, .3125, .5, .625, .75},
                    {.125, 0, .152343750, .875, .375, .9375},
                    {.125, .625, .125, .875, .875, .875}
            }, new double[][] {
                    {.125, .749869932, .597809015, .875, 1.769420322, 1.211462331},
                    {0, .625, .75, .125, 1.0625, .9375},
                    {0, .625, .15625, .125, .8125, .75},
                    {0, 1.0625, .1875, .125, 1.1875, .9375},
                    {.875, 1.0625, .1875, 1, 1.1875, .9375},
                    {.875, .625, .75, 1, 1.0625, .9375},
                    {.875, .625, .15625, 1, .8125, .75},
                    {.15625, 1.021865899, .959038950, .84375, 1.449980909, 1.184205422},
                    {.09375, .912360386, .904953521, .15625, 1.406674056, 1.145581519},
                    {.84375, .912360386, .904953521, .90625, 1.406674056, 1.145581519}
            }, new double[] {.5, .875, .5});
        }

        static Bounds cab() {
            try (var reader = new InputStreamReader(Objects.requireNonNull(Bounds.class
                    .getResourceAsStream("/data/hexie/crh2a_cab_seat_geometry.json")), StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, Bounds.class);
            } catch (IOException error) {
                throw new ExceptionInInitializerError(error);
            }
        }
    }

    public static VoxelShape boxes(double[][] bounds, Direction facing) {
        var shape = Shapes.empty();
        for (var b : bounds) {
            var box = new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
            shape = Shapes.or(shape, Shapes.create(BlockRotation.rotate(box, facing)));
        }
        return shape;
    }

}
