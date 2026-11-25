package willow.train.hexie.core.api.cab;

import willow.train.hexie.core.api.interaction.ControlState;

import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.core.api.traincontrol.CabTrainControl;

/** Connects a physical operating surface to a train. */
public interface CabControlHost extends CabSurfaceHost {
    default CabModuleAccess createCabModule() { return CabModuleAccess.Factory.create(); }

    default CabTrainControl trainControl(BlockState block) { return CabTrainControl.NONE; }

    /** A physical setting may remain operable even while the carriage is derailed. */
    default boolean operatesWhenDerailed(BlockState block, String controlId) { return false; }

    /** Physical detents for the common controller's power-zero and reverser-cut-out checks. */
    default CabNeutralPositions neutralPositions(BlockState block) { return CabNeutralPositions.FROM_REQUEST; }

    default ControlState surfaceState(BlockState block, CabState state) {
        var surface = state.surface(trainControl(block));
        for (var mount : surfaceGeometry(block).mounts())
            if (mount.module() instanceof CabTrainControl module)
                surface = module.restore(surface, state.request());
        return surface;
    }
}
