package willow.train.hexie.world.blocks.emu;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabControlBridge;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.api.cab.CabNeutralPositions;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.ControlInputLayout;
import willow.train.hexie.core.api.interaction.ControlSurface;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.blocks.misc.DrivingConsoleBlock;
import willow.train.hexie.world.collision.EmuCollision;

public class CRH2DrivingConsoleBlock extends DrivingConsoleBlock implements CarriageBlockBridge.CRH2DrivingConsoleBlockView {

    private final EmuBlockInteract.ForCRH2DrivingConsoleBlock interactionShapes = new EmuBlockInteract.ForCRH2DrivingConsoleBlock();

    public CRH2DrivingConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties, () -> ControlSurface.EMPTY, CabTrainControl.NONE);
    }

    public static CRH2DrivingConsoleBlock nonstandard(BlockBehaviour.Properties properties) {
        return new CRH2DrivingConsoleBlock(properties, CRH2CabGeometry::surface, HexieBridges.cabControl().crh2CabControls().instance(),
                HexieBridges.cabControl().crh2CabControls().neutralPositions());
    }

    private CRH2DrivingConsoleBlock(BlockBehaviour.Properties properties, Supplier<ControlSurface> geometry, CabTrainControl trainControl,
                                   CabNeutralPositions neutralPositions) {
        super(properties, geometry, trainControl, neutralPositions);
    }

    @Override public ControlInputLayout inputLayout(BlockControlContext target) {
        return interactionShapes.inputLayout(this, target, () -> super.inputLayout(target));
    }

    public static ControlInputLayout.Binding handleKey(int key, CabControlBridge.Crh2CabHandleView handle, int direction) {
        return EmuBlockInteract.ForCRH2DrivingConsoleBlock.handleKey(key, handle, direction);
    }

    public Component keyboardHint(BlockState state) {
        return interactionShapes.keyboardHint(this, state);
    }

    public static Component hintKeys(String first, String second) {
        return EmuBlockInteract.ForCRH2DrivingConsoleBlock.hintKeys(first, second);
    }

    public static Map<Direction, VoxelShape> createShapes() {
        return EmuCollision.ForCRH2DrivingConsoleBlock.createShapes();
    }

    @Override public AABB controlBounds(BlockState state) {
        return interactionShapes.controlBounds(this, state);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForCRH2DrivingConsoleBlock.getShape(state, level, pos, context);
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForCRH2DrivingConsoleBlock.getCollisionShape(state, level, pos, context);
    }
}
