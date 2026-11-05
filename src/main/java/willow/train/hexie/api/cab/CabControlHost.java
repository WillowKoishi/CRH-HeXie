package willow.train.hexie.api.cab;

import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.api.traincontrol.CabTrainControl;
import willow.train.hexie.vehicle.control.CabModule;

/** Connects a physical operating surface to a train. */
public interface CabControlHost extends CabSurfaceHost {
    default CabModule createCabModule() { return new CabModule(); }

    default CabTrainControl trainControl(BlockState block) { return CabTrainControl.NONE; }

    default CabSurfaceState surfaceState(BlockState block, CabModule.State state) {
        var surface = state.surface(trainControl(block));
        for (var mount : surfaceGeometry(block).mounts())
            if (mount.module() instanceof CabTrainControl module)
                surface = module.restore(surface, state.request());
        return surface;
    }
}
