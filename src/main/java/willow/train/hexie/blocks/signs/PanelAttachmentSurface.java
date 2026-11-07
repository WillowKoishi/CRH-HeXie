package willow.train.hexie.blocks.signs;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import willow.train.hexie.api.interaction.BlockControl;
import willow.train.hexie.api.interaction.BlockControlContext;
import willow.train.hexie.api.interaction.BlockControlHit;
import willow.train.hexie.api.interaction.ControlState;
import willow.train.hexie.api.interaction.ControlSurface;
import willow.train.hexie.blocks.parts.PartStructure;
import willow.train.hexie.blocks.parts.PartStructures;

/** Native click targets for installed screens, including panels whose mesh extends above their shape. */
public final class PanelAttachmentSurface {
    private static final double THICKNESS = 1d / 1024;
    private static final Map<BlockState, Geometry> CACHE = new IdentityHashMap<>();
    private enum Control implements BlockControl.Button {
        PANEL, SIGN, NUMBER;
        @Override public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
        @Override public Component nameText() { return Component.empty(); }
        @Override public Component valueText(ControlState state) { return Component.empty(); }
    }
    public static final ControlSurface SURFACE = new ControlSurface(java.util.Arrays.stream(Control.values())
            .map(control -> new ControlSurface.Part(control, null, ControlSurface.NO_BODY, null)).toList(),
            ControlSurface.NO_BODY, List.of());
    private record Screen(Control control, AABB box, Direction normal) {}
    private record Geometry(AABB bounds, List<Screen> screens) {}

    private PanelAttachmentSurface() {}

    public static AABB bounds(BlockState state) { return geometry(state).bounds(); }

    /** Keep the exact original outline available on every panel face, even with no screens installed. */
    public static BlockControlHit pick(BlockControlContext context, Vec3 from, Vec3 to) {
        var state = context.state();
        var owner = context.position();
        BlockHitResult nearest = state.getShape(context.blocks(), owner).clip(from, to, owner);
        Control control = Control.PANEL;
        for (var screen : geometry(state).screens()) {
            var hit = Shapes.create(screen.box()).clip(from, to, owner);
            if (hit == null || hit.getDirection() != screen.normal()
                    || nearest != null && nearest.getLocation().distanceToSqr(from) <= hit.getLocation().distanceToSqr(from)) continue;
            nearest = hit;
            control = screen.control();
        }
        if (nearest == null) return null;
        if (control != Control.PANEL && state.getBlock() instanceof PartStructure part) {
            // Existing occupied members keep native owner-cell packet/reach checks intact.
            // A one-cell end face with a modest visual overhang keeps its actual owner.
            var right = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise();
            var cell = owner.relative(right, PanelDisplayColumn.get(state).offset())
                    .above((int) Math.floor(nearest.getLocation().y - owner.getY()));
            if (part.partCells(context.blocks(), owner, state).containsKey(cell)) nearest = nearest.withPosition(cell);
        }
        return new BlockControlHit(control, nearest);
    }

    private static synchronized Geometry geometry(BlockState state) {
        return CACHE.computeIfAbsent(state, PanelAttachmentSurface::build);
    }

    private static Geometry build(BlockState state) {
        var shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        AABB original = shape.isEmpty() ? new AABB(BlockPos.ZERO) : shape.bounds();
        if (!PassengerDisplayPanel.supports(state.getBlock()) || !PassengerDisplayInstallation.isRoot(state)
                || !state.hasProperty(PanelDisplayMount.PROPERTY) || !PanelDisplayColumn.isValid(state)
                || !(state.getBlock() instanceof PartStructure part)) return new Geometry(original, List.of());
        var mount = state.getValue(PanelDisplayMount.PROPERTY);
        if (mount == PanelDisplayMount.NONE) return new Geometry(original, List.of());
        var panel = PanelAttachmentHit.bounds(EmptyBlockGetter.INSTANCE, new PartStructures.Root(BlockPos.ZERO, state, part));
        if (panel == null) return new Geometry(original, List.of());
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        var normal = facing.getOpposite();
        var right = facing.getClockWise();
        double depth = switch (normal) {
            case EAST -> original.maxX;
            case WEST -> original.minX;
            case SOUTH -> original.maxZ;
            case NORTH -> original.minZ;
            default -> throw new IllegalStateException("Panel face must be horizontal");
        };
        double column = PanelDisplayColumn.get(state).offset();
        double handed = mount.numberLeft() ? 1 : -1;
        var screens = new ArrayList<Screen>(2);
        if (mount.hasSign()) screens.add(screen(Control.SIGN, right, normal, depth, panel.maxY - .25,
                column + handed * 2.2 / 16, 9.5 / 16, 2.75 / 16));
        if (mount.hasNumber()) screens.add(screen(Control.NUMBER, right, normal, depth, panel.maxY - .25,
                column - handed * 5.9 / 16, 2.1 / 16, 2.05 / 16));
        AABB envelope = original;
        for (var screen : screens) envelope = envelope.minmax(screen.box());
        return new Geometry(envelope, List.copyOf(screens));
    }

    private static Screen screen(Control control, Direction right, Direction normal, double depth,
            double y, double horizontal, double width, double height) {
        double x = .5 + right.getStepX() * horizontal, z = .5 + right.getStepZ() * horizontal;
        AABB box = normal.getAxis() == Direction.Axis.X
                ? new AABB(depth - THICKNESS, y - height / 2, z - width / 2,
                        depth + THICKNESS, y + height / 2, z + width / 2)
                : new AABB(x - width / 2, y - height / 2, depth - THICKNESS,
                        x + width / 2, y + height / 2, depth + THICKNESS);
        return new Screen(control, box, normal);
    }
}
