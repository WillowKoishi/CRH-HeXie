package willow.train.hexie.world.blocks.carriage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.world.blocks.base.SideMirrorBlockBase;
import willow.train.hexie.world.blocks.parts.LegacyPartLayout;
import willow.train.hexie.world.blocks.signs.PassengerDisplayPanel;
import willow.train.hexie.world.collision.CarriageCollisionShapes;

public class PanelBlock25Side extends SideMirrorBlockBase implements PassengerDisplayPanel {

    private final boolean passengerDisplays;
    @Override public boolean supportsPassengerDisplay() { return passengerDisplays; }

    public VoxelShape getShape(
            BlockState p_54372_,
            BlockGetter p_54373_,
            BlockPos p_54374_,
            CollisionContext p_54375_) {
        return CarriageCollisionShapes.ForPanelBlock25Side.getShape(p_54372_, p_54373_, p_54374_, p_54375_);
    }

    public PanelBlock25Side(BlockBehaviour.Properties pProperties) {
        this(pProperties, true);
    }

    public PanelBlock25Side(BlockBehaviour.Properties pProperties, boolean passengerDisplays) {
        super(pProperties);
        this.passengerDisplays = passengerDisplays;
        this.registerDefaultState(
                this.stateDefinition
                        .any().setValue(LegacyPartLayout.OCCUPIED,false)
                        .setValue(FACING, Direction.NORTH)
                        .setValue(HINGE, DoorHingeSide.LEFT));
    }
}
