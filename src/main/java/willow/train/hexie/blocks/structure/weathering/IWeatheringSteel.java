package willow.train.hexie.blocks.structure.weathering;

import com.google.common.base.Suppliers;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.init.block.WeatheringResistantSteelBlockInit;

import java.util.Optional;
import java.util.function.Supplier;

public interface IWeatheringSteel extends IChange6OverTimeBlock<IWeatheringSteel.WSWeatherState> {
    Supplier<BiMap<Block, Block>> NEXT_BY_BLOCK = Suppliers.memoize(() -> {
        return ImmutableBiMap
                .<Block, Block>builder()
                .put(WeatheringResistantSteelBlockInit.WEATHERING_STEEL_BLOCK.get(),WeatheringResistantSteelBlockInit.EXPOSED_WEATHERING_STEEL_BLOCK.get())
                .put(WeatheringResistantSteelBlockInit.EXPOSED_WEATHERING_STEEL_BLOCK.get(), WeatheringResistantSteelBlockInit.YELLOWING_WEATHERING_STEEL_BLOCK.get())
                .put(WeatheringResistantSteelBlockInit.YELLOWING_WEATHERING_STEEL_BLOCK.get(), WeatheringResistantSteelBlockInit.RUSTED_WEATHERING_STEEL_BLOCK.get())
                .put(WeatheringResistantSteelBlockInit.RUSTED_WEATHERING_STEEL_BLOCK.get(), WeatheringResistantSteelBlockInit.STABLY_RUSTED_WEATHERING_STEEL_BLOCK.get())
                .put(WeatheringResistantSteelBlockInit.STABLY_RUSTED_WEATHERING_STEEL_BLOCK.get(), WeatheringResistantSteelBlockInit.FULLY_RUSTED_WEATHERING_STEEL_BLOCK.get())
                .build();
    });
    Supplier<BiMap<Block, Block>> PREVIOUS_BY_BLOCK = Suppliers.memoize(() -> {
        return NEXT_BY_BLOCK.get().inverse();
    });

    static Optional<Block> getPrevious(Block pBlock) {
        return Optional.ofNullable(PREVIOUS_BY_BLOCK.get().get(pBlock));
    }

    static Block getFirst(Block pBlock) {
        Block block = pBlock;

        for(Block block1 = PREVIOUS_BY_BLOCK.get().get(pBlock); block1 != null; block1 = PREVIOUS_BY_BLOCK.get().get(block1)) {
            block = block1;
        }

        return block;
    }

    static Optional<BlockState> getPrevious(BlockState pState) {
        return getPrevious(pState.getBlock()).map((p_154903_) -> {
            return p_154903_.withPropertiesOf(pState);
        });
    }

    static Optional<Block> getNext(Block pBlock) {
        return Optional.ofNullable(NEXT_BY_BLOCK.get().get(pBlock));
    }

    static BlockState getFirst(BlockState pState) {
        return getFirst(pState.getBlock()).withPropertiesOf(pState);
    }

    default Optional<BlockState> getNext(BlockState pState) {
        return getNext(pState.getBlock()).map((p_154896_) -> {
            return p_154896_.withPropertiesOf(pState);
        });
    }

    default float getChanceModifier() {
        return this.getAge() == WSWeatherState.WS ? 0.75F : 1.0F;
    }


    public static enum WSWeatherState {
        WS,
        EXPOSED_WS,
        YELLOWING_WS,
        RUSTED_WS,
        STABLY_WS,
        FULLY_WS;
    }
}
