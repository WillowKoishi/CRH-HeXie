package willow.train.hexie.blocks.emu;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.api.cab.CabNeutralPositions;
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.api.traincontrol.CabTrainControl;
import willow.train.hexie.blocks.DrivingConsoleBlock;
import willow.train.hexie.vehicle.control.Crh2CabControls;

public class CRH2DrivingConsoleBlock extends DrivingConsoleBlock {
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    public CRH2DrivingConsoleBlock(Properties properties) {
        super(properties, () -> ControlSurface.EMPTY, CabTrainControl.NONE);
    }

    public static CRH2DrivingConsoleBlock nonstandard(Properties properties) {
        return new CRH2DrivingConsoleBlock(properties, CRH2CabGeometry::surface, Crh2CabControls.INSTANCE,
                Crh2CabControls.NEUTRAL_POSITIONS);
    }

    private CRH2DrivingConsoleBlock(Properties properties, Supplier<ControlSurface> geometry, CabTrainControl trainControl,
                                   CabNeutralPositions neutralPositions) {
        super(properties, geometry, trainControl, neutralPositions);
    }

    private static Map<Direction, VoxelShape> createShapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        // OBJ coordinates, shifted to the block centre in X/Z. Only the two cabinets
        // and the central vertical panel are solid; omit the pedal and desktop.
        double[][] boxes = {
                {-1.0125, 0, -.04375, -.375, 1, .4},
                {-.375, 0, -.04375, .375, 1, .025},
                {.375, 0, -.04375, 1.0125, 1, .4}
        };
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                double minX = Double.POSITIVE_INFINITY, minZ = minX;
                double maxX = Double.NEGATIVE_INFINITY, maxZ = maxX;
                for (double x : new double[] {b[0], b[3]})
                    for (double z : new double[] {b[2], b[5]}) {
                        double rx = switch (facing) {
                            case EAST -> -z;
                            case SOUTH -> -x;
                            case WEST -> z;
                            default -> x;
                        };
                        double rz = switch (facing) {
                            case EAST -> x;
                            case SOUTH -> -z;
                            case WEST -> -x;
                            default -> z;
                        };
                        minX = Math.min(minX, rx + .5); maxX = Math.max(maxX, rx + .5);
                        minZ = Math.min(minZ, rz + .5); maxZ = Math.max(maxZ, rz + .5);
                    }
                shape = Shapes.or(shape, Shapes.box(minX, b[1], minZ, maxX, b[4], maxZ));
            }
            shapes.put(facing, shape);
        }
        return shapes;
    }

    @Override public AABB controlBounds(BlockState state) {
        return surfaceGeometry(state) == ControlSurface.EMPTY ? null : CRH2CabGeometry.controlBounds(cabFacing(state));
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
