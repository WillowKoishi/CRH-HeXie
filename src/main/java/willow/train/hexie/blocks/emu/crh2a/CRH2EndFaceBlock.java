package willow.train.hexie.blocks.emu.crh2a;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.blocks.coupler.AbstractCouplerBlock;
import willow.train.hexie.blocks.emu.head.HeadGeometry;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Point;
import willow.train.hexie.vehicle.runtime.CouplingEndpoint.Axis;

/** End wall, gangways and coupler share only the central floor installation block. */
public final class CRH2EndFaceBlock extends AbstractCouplerBlock {
    public static final MapCodec<CRH2EndFaceBlock> CODEC = simpleCodec(CRH2EndFaceBlock::new);
    public static final Axis AXIS = new Axis(0, -.08125);
    public static final double BELLOWS_BASE_Z = -1 / 16d;
    public static final double BELLOWS_FRONT_Z = -5 / 16d;
    // User-selected maximum (model units), shared by free-state rendering and contact onset.
    public static final double MAX_BELLOWS_THICKNESS = 5 / 16d;
    private static final VoxelShape FLOOR = Shapes.block();
    private static final Map<Direction, List<AABB>> PLAYER_BOXES = playerBoxes();

    public CRH2EndFaceBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected VoxelShape couplerShape(BlockState state) { return FLOOR; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return FLOOR;
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return FLOOR;
    }
    @Override public String model() { return "hexie:crh2_semiauto"; }
    @Override public Axis axis() { return AXIS; }
    @Override public Point start() { return new Point(0, -.05, BELLOWS_BASE_Z - MAX_BELLOWS_THICKNESS - .5); }
    @Override public Point complete() { return new Point(0, -.05, -.8125); }
    @Override public double maxExtension() {
        return 2 * (MAX_BELLOWS_THICKNESS - (BELLOWS_BASE_Z - BELLOWS_FRONT_Z));
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        return defaultBlockState().setValue(FACING,
                face.getAxis().isHorizontal() ? face : context.getHorizontalDirection());
    }

    @Override public void onBlockStateChange(LevelReader world, BlockPos pos, BlockState oldState, BlockState newState) {
        if (world instanceof Level level && level.isClientSide)
            willow.train.hexie.client.render.CRH2StaticSections.changed(world, pos, oldState, newState);
    }

    @Override public BlockState carriageRenderState(BlockState state, boolean shiftedBase) {
        // The wall follows the carriage floor. Only the arm is placed by the coupler animation.
        return state.setValue(ASSEMBLED, true);
    }

    public static List<AABB> playerBoxes(Direction facing) { return PLAYER_BOXES.get(facing); }
    public static AABB bounds(Direction facing) {
        return HeadGeometry.rotate(new AABB(-19 / 16d, -.0875, -.5, 35 / 16d, 3.5, 2), facing);
    }

    private static Map<Direction, List<AABB>> playerBoxes() {
        // Export coordinates: x=.5-X/16, y=1+Y/16, z=(200-Z)/16.
        // Both the end wall and inner gangway leave the requested [0,1] x [1,3] opening.
        List<AABB> north = List.of(
                new AABB(-19 / 16d, 13 / 16d, -1 / 16d, 0, 3.5, 0),
                new AABB(1, 13 / 16d, -1 / 16d, 35 / 16d, 3.5, 0),
                new AABB(0, 3, -1 / 16d, 1, 3.5, 0),
                new AABB(0, 13 / 16d, -1 / 16d, 1, 1, 0),
                new AABB(-2 / 16d, 14 / 16d, -5 / 16d, 0, 51 / 16d, -1 / 16d),
                new AABB(1, 14 / 16d, -5 / 16d, 18 / 16d, 51 / 16d, -1 / 16d),
                new AABB(0, 3, -5 / 16d, 1, 51 / 16d, -1 / 16d),
                new AABB(0, 14 / 16d, -5 / 16d, 1, 1, -1 / 16d),
                // Only the outward 3/16 slab collides, matching CRH2A_SKIRT_NORMAL.
                new AABB(-19 / 16d, -.0875, -1 / 16d, -1, 1, 2),
                new AABB(2, -.0875, -1 / 16d, 35 / 16d, 1, 2));
        Map<Direction, List<AABB>> result = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL)
            result.put(facing, north.stream().map(box -> HeadGeometry.rotate(box, facing)).toList());
        return Map.copyOf(result);
    }
}
