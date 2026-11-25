package willow.train.hexie.world.blocks.locos;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.fml.ModList;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.base.PantographBlockBase;

public class PantographBlock extends PantographBlockBase {
    public static Block create(BlockBehaviour.Properties properties) {
        if (ModList.get().isLoaded("electroenergetics")) {
            return HexieBridges.cee().createBlock(properties).asBlock();
        }
        return new PantographBlock(properties);
    }

    public PantographBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
