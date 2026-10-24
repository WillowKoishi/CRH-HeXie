package willow.train.hexie.init.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import willow.train.hexie.blocks.structure.weathering.FullWeatheringSteelBlock;
import willow.train.hexie.blocks.structure.weathering.IWeatheringSteel;

import static willow.train.hexie.init.block.BlockInit.register;

public class WeatheringResistantSteelBlockInit {
    //耐候钢块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> WEATHERING_STEEL_BLOCK = register("weathering_resistant_steel_block_0",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //斑驳的耐候钢块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> EXPOSED_WEATHERING_STEEL_BLOCK = register("exposed_weathering_steel_block",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.EXPOSED_WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //泛黄的耐候钢块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> YELLOWING_WEATHERING_STEEL_BLOCK = register("yellowing_weathering_steel_block",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.YELLOWING_WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //耐候钢锈块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> RUSTED_WEATHERING_STEEL_BLOCK = register("rusted_weathering_steel_block",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.RUSTED_WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //稳定的耐候钢锈块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> STABLY_RUSTED_WEATHERING_STEEL_BLOCK = register("stably_rusted_weathering_steel_block",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.STABLY_WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //致密的耐候钢锈块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> FULLY_RUSTED_WEATHERING_STEEL_BLOCK = register("fully_rusted_weathering_steel_block",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.FULLY_WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static void invoke(){};
}
