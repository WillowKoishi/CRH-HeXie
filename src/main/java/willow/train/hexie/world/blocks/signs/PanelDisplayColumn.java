package willow.train.hexie.world.blocks.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import willow.train.hexie.bridge.world.PartBlockBridge.PartStructureView;
import willow.train.hexie.bridge.world.SignBlockBridge;

/** Horizontal installation cell relative to the panel root, viewed from the painted exterior. */
public enum PanelDisplayColumn implements StringRepresentable {
    ROOT("root", 0), NEGATIVE("negative", -1), POSITIVE("positive", 1);

    public static final EnumProperty<SignBlockBridge.PanelDisplayColumn> PROPERTY = EnumProperty.create("display_column", SignBlockBridge.PanelDisplayColumn.class);
    private final String name;
    private final int offset;

    PanelDisplayColumn(String name, int offset) { this.name = name; this.offset = offset; }
    @Override public String getSerializedName() { return name; }
    public int offset() { return offset; }

    public static PanelDisplayColumn get(BlockState state) {
        return state.hasProperty(PROPERTY) ? valueOf(state.getValue(PROPERTY).name()) : ROOT;
    }

    /** Upper and lower members share a column; height never changes the installation row. */
    public static PanelDisplayColumn fromClick(BlockState state, BlockPos root, BlockPos clicked) {
        int column = project(state, clicked.subtract(root));
        for (var value : values()) if (value.offset == column) return value;
        return null;
    }

    public static boolean isValid(BlockState state) {
        if (!PassengerDisplayPanel.supports(state.getBlock())
                || !(state.getBlock() instanceof PartStructureView part)) return false;
        int selected = get(state).offset;
        return part.partCells(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, state).keySet().stream()
                .anyMatch(pos -> project(state, pos) == selected);
    }

    private static int project(BlockState state, BlockPos offset) {
        Direction right = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise();
        return offset.getX() * right.getStepX() + offset.getZ() * right.getStepZ();
    }

    /** A horizontal reflection reverses exterior-right; rotations already rotate FACING. */
    public static BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE || !state.hasProperty(PROPERTY)) return state;
        return state.setValue(PROPERTY, switch (get(state)) {
            case ROOT -> SignBlockBridge.PanelDisplayColumn.ROOT;
            case NEGATIVE -> SignBlockBridge.PanelDisplayColumn.POSITIVE;
            case POSITIVE -> SignBlockBridge.PanelDisplayColumn.NEGATIVE;
        });
    }

    public SignBlockBridge.PanelDisplayColumn stored() { return SignBlockBridge.PanelDisplayColumn.valueOf(name()); }
}
