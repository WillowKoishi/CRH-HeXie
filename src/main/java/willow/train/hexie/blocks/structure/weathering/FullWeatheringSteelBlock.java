package willow.train.hexie.blocks.structure.weathering;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class FullWeatheringSteelBlock extends Block implements IWeatheringSteel{
    private final IWeatheringSteel.WSWeatherState wsWeatherState;
    public FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState wsWeatherState ,Properties properties) {
        super(properties);
        this.wsWeatherState = wsWeatherState;
    }

    /**
     * Performs a random tick on a block.
     */
    public void randomTick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
        this.onRandomTick(pState, pLevel, pPos, pRandom);
    }

    /**
     * @return whether this block needs random ticking.
     */
    public boolean isRandomlyTicking(BlockState pState) {
        return IWeatheringSteel.getNext(pState.getBlock()).isPresent();
    }

    public IWeatheringSteel.WSWeatherState getAge() {
        return this.wsWeatherState;
    }
}