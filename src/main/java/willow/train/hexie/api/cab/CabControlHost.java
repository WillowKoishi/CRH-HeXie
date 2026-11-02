package willow.train.hexie.api.cab;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.vehicle.control.CabModule;

/** Input-surface adapter. Runtime control and display data belong to the shared cab module. */
public interface CabControlHost {
    Direction cabFacing(BlockState state);
    default CabModule createCabModule() { return new CabModule(); }
}
