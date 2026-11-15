package willow.train.hexie.blocks.emu.crh1a;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.api.cab.CabTargetView;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlUse;
import willow.train.hexie.api.interaction.ControlInput;
import willow.train.hexie.api.interaction.ControlInputLayout;
import willow.train.hexie.blocks.DrivingConsoleBlock;
import willow.train.hexie.vehicle.control.Crh1CabControls;
import willow.train.hexie.vehicle.control.Crh1CabHandle;
import willow.train.hexie.vehicle.control.CabModule;

/** CRH1A's single-controller cab uses the same seat and vehicle adapter as the CRH2A desk. */
public final class CRH1DrivingConsoleBlock extends DrivingConsoleBlock {
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();
    private final Map<BlockState, ControlInputLayout> detailedLayouts = new ConcurrentHashMap<>();
    private final Map<BlockState, ControlInputLayout> simpleLayouts = new ConcurrentHashMap<>();

    public CRH1DrivingConsoleBlock(Properties properties) {
        super(properties, CRH1CabGeometry::surface, Crh1CabControls.INSTANCE, Crh1CabControls.NEUTRAL_POSITIONS);
    }

    @Override public CabModule createCabModule() {
        var module = new CabModule();
        module.submit(CabModule.normalize(Crh1CabControls.INSTANCE, module.state()));
        return module;
    }

    @Override public boolean operatesWhenDerailed(BlockState state, String controlId) {
        return Crh1CabControls.DirectionButton.INSTANCE.id().equals(controlId);
    }

    @Override public ControlInputLayout inputLayout(BlockControlContext target) {
        var base = super.inputLayout(target);
        boolean simple = CabTargetView.find(target).controls().simple();
        var layouts = simple ? simpleLayouts : detailedLayouts;
        return layouts.computeIfAbsent(target.state(), ignored -> {
            var bindings = new ArrayList<>(base.bindings());
            if (!simple) {
                bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, 'W', ControlInputLayout.Action.STEP_HOLD,
                        Crh1CabHandle.INSTANCE.id(), 1).seated());
                bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, 'S', ControlInputLayout.Action.STEP_HOLD,
                        Crh1CabHandle.INSTANCE.id(), -1).seated());
            }
            bindings.add(new ControlInputLayout.Binding(ControlInputLayout.Device.KEY, 'R', ControlInputLayout.Action.PRESS,
                    Crh1CabControls.DirectionButton.INSTANCE.id(), 0).seated());
            return new ControlInputLayout(base.seatedCapture(), base.pointedEntries(), bindings, base.view());
        });
    }

    @Override public void endCapture(BlockControlUse use, boolean seated, boolean explicit) {
        int current = Crh1CabHandle.INSTANCE.notch(surfaceState(use.target()));
        int released = Crh1CabHandle.INSTANCE.releasedNotch(current);
        if (released != current)
            super.operateInput(use, new ControlInput(Crh1CabHandle.INSTANCE.id(), released, ControlInput.Kind.SET, java.util.Set.of()));
        super.endCapture(use, seated, explicit);
    }

    @Override public AABB controlBounds(BlockState state) { return CRH1CabGeometry.controlBounds(cabFacing(state)); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    private static Map<Direction, VoxelShape> createShapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        AABB[] north = {
                new AABB(-.663, 0, -.082, .125, .875, .919),
                new AABB(.875, 0, -.082, 1.663, .875, .919),
                new AABB(.125, 0, -.082, .875, .8125, 0)
        };
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (AABB box : north) {
                AABB rotated = switch (facing) {
                    case EAST -> new AABB(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX);
                    case SOUTH -> new AABB(1 - box.maxX, box.minY, 1 - box.maxZ, 1 - box.minX, box.maxY, 1 - box.minZ);
                    case WEST -> new AABB(box.minZ, box.minY, 1 - box.maxX, box.maxZ, box.maxY, 1 - box.minX);
                    default -> box;
                };
                shape = Shapes.or(shape, Shapes.create(rotated));
            }
            shapes.put(facing, shape);
        }
        return Map.copyOf(shapes);
    }
}
