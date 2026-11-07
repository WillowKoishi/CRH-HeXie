package willow.train.hexie.blocks.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import willow.train.hexie.blocks.emu.crh2a.CRH2PartBlock;
import willow.train.hexie.blocks.emu.crh2a.HarmonyMarking;
import willow.train.hexie.blocks.parts.PartStructures;
import willow.train.hexie.blocks.parts.VehicleMarkingSurface;
import willow.train.hexie.blocks.parts.VehicleMarkings;

/** Common-side attachment picking; positions remain in the logical panel owner's coordinates. */
public final class PanelAttachmentHit {
    public enum Kind { NONE, SIGN, NUMBER, MARKING_FIELDS, PANEL_TEXT, HARMONY, LEGACY }
    public record Target(BlockPos pos, BlockState state, Kind kind, int slot) {}
    private static final double EPS = 1e-6;

    private PanelAttachmentHit() {}

    public static Target resolve(BlockGetter level, BlockPos clicked, BlockHitResult hit) {
        var root = PartStructures.resolve(level, clicked);
        BlockPos pos = root == null ? clicked : root.pos();
        BlockState state = root == null ? level.getBlockState(clicked) : root.state();
        var none = new Target(pos, state, Kind.NONE, 0);
        if (root != null && PassengerDisplayPanel.supports(state.getBlock()) && exterior(state, hit)) {
            var bounds = bounds(level, root);
            if (bounds != null && PanelDisplayColumn.isValid(state)) {
                var mount = state.getValue(PanelDisplayMount.PROPERTY);
                double x = horizontal(state, pos, hit.getLocation()) - PanelDisplayColumn.get(state).offset();
                double y = hit.getLocation().y - pos.getY() - (bounds.maxY - .25);
                double handed = mount.numberLeft() ? 1 : -1;
                if (mount.hasSign() && rectangle(x, y, handed * 2.2 / 16, 0, 9.5 / 16, 2.75 / 16))
                    return new Target(pos, state, Kind.SIGN, 0);
                if (mount.hasNumber() && rectangle(x, y, -handed * 5.9 / 16, 0, 2.1 / 16, 2.05 / 16))
                    return new Target(pos, state, Kind.NUMBER, 0);
            }
        }
        if (state.getBlock() instanceof TrainPanelBlock || state.getBlock() instanceof TrainNoBlock
                || state.getBlock() instanceof LacqueredBoardBlock || state.getBlock() instanceof TrainSpeedSignBlock)
            return new Target(pos, state, Kind.LEGACY, 0);
        if (!(state.getBlock() instanceof VehicleMarkingSurface surface)
                || !(level.getBlockEntity(pos) instanceof VehicleMarkings markings)
                || hit.getDirection() != surface.markingFacing(state).getOpposite()) return none;
        var local = markingPoint(surface.markingFacing(state), pos, hit.getLocation());
        var area = surface.markingBounds(state);
        int slots = Math.min(2, surface.markingSlots(state));
        for (int slot = 0; slot < slots; slot++) {
            var label = markings.panelText(slot);
            if (label.text().isBlank()) continue;
            var size = PanelAttachmentTextMetrics.measure(label.font(), label.text(), label.size() / 16d,
                    Double.MAX_VALUE, label.spacing() / 100f);
            double x = area.minX + slot + .5 + label.x() / 64d;
            double top = (area.minY + area.maxY) / 2 + label.y() / 64d;
            if (rectangle(local.x, local.y, x, top - size.height() / 2, size.width(), size.height()))
                return new Target(pos, state, Kind.PANEL_TEXT, slot);
        }
        for (int slot = 0; slot < slots; slot++) {
            int direction = markings.harmony(slot);
            if (direction == 0) continue;
            boolean right = direction == 2;
            var logo = HarmonyMarking.layout(area.minX + slot, area.maxY, right);
            // Bounds of nontransparent pixels in the existing 500x123 / 500x131 logo atlases.
            double left = logo.left() + logo.width() * (right ? 1 : 0) / 500;
            double end = logo.left() + logo.width() * (right ? 500 : 471) / 500;
            double top = logo.top() - logo.height() * (right ? 20d / 131 : 19d / 123);
            double bottom = logo.top() - logo.height() * (right ? 99d / 131 : 93d / 123);
            if (rectangle(local.x, local.y, (left + end) / 2, (top + bottom) / 2, end - left, top - bottom))
                return new Target(pos, state, Kind.HARMONY, slot);
        }
        int slot = slots > 1 ? Math.clamp(markings.markingSlot(), 0, slots - 1) : 0;
        String[] fields = markings.fields();
        if (fields.length >= 3) {
            var number = PanelAttachmentTextMetrics.measure("hexie:head_number", fields[0], .132, .88, 0);
            var chinese = PanelAttachmentTextMetrics.measure("hexie:carriage_marking_chinese", fields[1], .092, .64, 0);
            var english = PanelAttachmentTextMetrics.measure("hexie:vehicle_pinyin", fields[2], .044, .64, 0);
            double descriptionWidth = Math.max(chinese.width(), english.width());
            double cellLeft = area.minX + slot;
            double anchor = markings.descriptionLeft() ? cellLeft + .125 : cellLeft + 1 - .125;
            double descriptionCenter = anchor + (markings.descriptionLeft() ? descriptionWidth / 2 : -descriptionWidth / 2);
            double numberLeft = markings.descriptionLeft() ? anchor + descriptionWidth + .085
                    : anchor - descriptionWidth - .085 - number.width();
            if (line(local, numberLeft + number.width() / 2, .15, number)
                    || line(local, descriptionCenter, .21, chinese) || line(local, descriptionCenter, .15, english))
                return new Target(pos, state, Kind.MARKING_FIELDS, slot);
        }
        return none;
    }

    /** Clipboard may populate the lower marking band without inventing a removable text/logo item. */
    public static Target emptyMarkingTarget(BlockGetter level, BlockPos clicked, BlockHitResult hit) {
        var target = resolve(level, clicked, hit);
        if (target.kind() != Kind.NONE) return target;
        var root = PartStructures.resolve(level, clicked);
        int slot = root == null ? -1 : lowerMarkingSlot(level, root, hit);
        if (slot < 0) return target;
        return new Target(target.pos(), target.state(), Kind.MARKING_FIELDS, slot);
    }

    /** Lower panel clicks retain the marking editor's cell selection, even before text exists. */
    public static int lowerMarkingSlot(BlockGetter level, PartStructures.Root root, BlockHitResult hit) {
        if (!(root.state().getBlock() instanceof VehicleMarkingSurface surface)
                || !(level.getBlockEntity(root.pos()) instanceof VehicleMarkings)
                || hit.getDirection() != surface.markingFacing(root.state()).getOpposite()) return -1;
        var point = markingPoint(surface.markingFacing(root.state()), root.pos(), hit.getLocation());
        var bounds = surface.markingBounds(root.state());
        int slots = Math.min(2, surface.markingSlots(root.state()));
        int slot = (int) Math.floor(point.x - bounds.minX);
        return slot >= 0 && slot < slots && point.y >= bounds.minY - EPS
                && point.y < bounds.maxY - 1 - EPS ? slot : -1;
    }

    /** Empty-hand swap keeps the original "click the upper panel" interaction, in its installed column only. */
    public static boolean displayRegion(BlockGetter level, PartStructures.Root root, BlockHitResult hit) {
        var state = root.state();
        if (!PassengerDisplayPanel.supports(state.getBlock()) || !state.hasProperty(PanelDisplayMount.PROPERTY)
                || state.getValue(PanelDisplayMount.PROPERTY) == PanelDisplayMount.NONE
                || !PanelDisplayColumn.isValid(state) || !exterior(state, hit)) return false;
        var bounds = bounds(level, root);
        if (bounds == null) return false;
        double x = horizontal(state, root.pos(), hit.getLocation()) - PanelDisplayColumn.get(state).offset();
        double y = hit.getLocation().y - root.pos().getY();
        return x >= -.5 - EPS && x < .5 - EPS && y >= Math.max(bounds.minY, bounds.maxY - 1) - EPS
                && y <= bounds.maxY + EPS;
    }

    public static AABB bounds(BlockGetter level, PartStructures.Root root) {
        if (root.state().getBlock() instanceof CRH2PartBlock panel) return panel.placementBounds(root.state());
        var shape = root.state().getShape(level, root.pos());
        if (shape.isEmpty()) return null;
        var bounds = shape.bounds();
        double top = PanelAttachmentGeometry.top(root.state(), bounds.maxY);
        return new AABB(bounds.minX, Math.min(bounds.minY, top), bounds.minZ, bounds.maxX, top, bounds.maxZ);
    }

    private static boolean exterior(BlockState state, BlockHitResult hit) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && hit.getDirection() == state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
    }

    private static double horizontal(BlockState state, BlockPos root, Vec3 point) {
        var right = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise();
        return (point.x - root.getX() - .5) * right.getStepX() + (point.z - root.getZ() - .5) * right.getStepZ();
    }

    private static Vec3 markingPoint(Direction facing, BlockPos root, Vec3 point) {
        double x = point.x - root.getX() - .5, z = point.z - root.getZ() - .5;
        return new Vec3(.5 + switch (facing) {
            case EAST -> z; case SOUTH -> -x; case WEST -> -z; default -> x;
        }, point.y - root.getY(), 0);
    }

    private static boolean line(Vec3 point, double x, double bottom, PanelAttachmentTextMetrics.Size size) {
        return rectangle(point.x, point.y, x, bottom + size.height() / 2, size.width(), size.height());
    }

    private static boolean rectangle(double x, double y, double centerX, double centerY, double width, double height) {
        return width > 0 && height > 0 && Math.abs(x - centerX) <= width / 2 + EPS && Math.abs(y - centerY) <= height / 2 + EPS;
    }
}
