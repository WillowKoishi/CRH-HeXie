package willow.train.hexie.world.blocks.emu;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.CabModuleView;
import willow.train.hexie.bridge.world.CarriageBlockBridge;
import willow.train.hexie.core.api.interaction.BlockControlContext;
import willow.train.hexie.core.api.interaction.BlockControlUse;
import willow.train.hexie.core.api.interaction.ControlInputLayout;
import willow.train.hexie.world.blockinteract.EmuBlockInteract;
import willow.train.hexie.world.blocks.misc.DrivingConsoleBlock;
import willow.train.hexie.world.collision.EmuCollision;

/** CRH1A's single-controller cab uses the same seat and vehicle adapter as the CRH2A desk. */
public final class CRH1DrivingConsoleBlock extends DrivingConsoleBlock implements CarriageBlockBridge.CRH1DrivingConsoleBlockView {

    private final EmuBlockInteract.ForCRH1DrivingConsoleBlock interactionShapes = new EmuBlockInteract.ForCRH1DrivingConsoleBlock();

    public CRH1DrivingConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties, CRH1CabGeometry::surface, HexieBridges.cabControl().crh1CabControls().instance(), HexieBridges.cabControl().crh1CabControls().neutralPositions());
    }

    @Override public CabModuleView createCabModule() {
        var module = HexieBridges.cabControl().createCabModule();
        module.submit(HexieBridges.cabControl().cabModule().normalize(HexieBridges.cabControl().crh1CabControls().instance(), module.state()));
        return module;
    }

    @Override public boolean operatesWhenDerailed(BlockState state, String controlId) {
        return HexieBridges.cabControl().crh1CabControlsDirectionButton().instance().id().equals(controlId);
    }

    @Override public ControlInputLayout inputLayout(BlockControlContext target) {
        return interactionShapes.inputLayout(target, () -> super.inputLayout(target));
    }

    @Override public void endCapture(BlockControlUse use, boolean seated, boolean explicit) {
        interactionShapes.endCapture(this, use, seated, explicit, super::operateInput, () -> super.endCapture(use, seated, explicit));
    }

    @Override public AABB controlBounds(BlockState state) {
        return interactionShapes.controlBounds(this, state);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForCRH1DrivingConsoleBlock.getShape(state, level, pos, context);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EmuCollision.ForCRH1DrivingConsoleBlock.getCollisionShape(state, level, pos, context);
    }

    public static Map<Direction, VoxelShape> createShapes() {
        return EmuCollision.ForCRH1DrivingConsoleBlock.createShapes();
    }
}
