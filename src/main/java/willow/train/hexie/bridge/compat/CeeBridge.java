package willow.train.hexie.bridge.compat;

import net.minecraft.world.level.block.state.BlockBehaviour;
import willow.train.hexie.bridge.world.CarriageBlockBridge;

public interface CeeBridge {
    CarriageBlockBridge.PantographBlockView createBlock(BlockBehaviour.Properties properties);
}
