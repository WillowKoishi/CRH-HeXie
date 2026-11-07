package willow.train.hexie.blocks.carriage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import willow.train.hexie.blocks.base.SideMirrorBlockBase;

public class PanelBlock25Side extends SideMirrorBlockBase implements willow.train.hexie.blocks.signs.PassengerDisplayPanel {

    private final boolean passengerDisplays;
    @Override public boolean supportsPassengerDisplay() { return passengerDisplays; }

protected static final VoxelShape WEST_AABB = Block.box(0, 0.0D, 0.0D, 1, 16.0D, 16.0D);
protected static final VoxelShape EAST_AABB = Block.box(15, 0.0D, 0D, 16.0D, 16.0D, 16.0D);

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return willow.train.hexie.blocks.parts.PartShapeDefinitions.get("edge_panel_1",p_54372_.getValue(FACING));
    }

    public PanelBlock25Side(Properties pProperties) {
        this(pProperties, true);
    }

    public PanelBlock25Side(Properties pProperties, boolean passengerDisplays) {
        super(pProperties);
        this.passengerDisplays = passengerDisplays;
        this.registerDefaultState(
                this.stateDefinition
                        .any().setValue(willow.train.hexie.blocks.parts.LegacyPartLayout.OCCUPIED,false)
                        .setValue(FACING, Direction.NORTH)
                        .setValue(HINGE, DoorHingeSide.LEFT));
    }
}
