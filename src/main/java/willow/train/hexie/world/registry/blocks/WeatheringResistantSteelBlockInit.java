package willow.train.hexie.world.registry.blocks;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import willow.train.hexie.world.blocks.structure.FullWeatheringSteelBlock;
import willow.train.hexie.world.blocks.structure.IWeatheringSteel;

public class WeatheringResistantSteelBlockInit {
    //耐候钢块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> WEATHERING_STEEL_BLOCK = BlockInit.register("weathering_resistant_steel_block_0",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //斑驳的耐候钢块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> EXPOSED_WEATHERING_STEEL_BLOCK = BlockInit.register("exposed_weathering_steel_block",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.EXPOSED_WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));
    //泛黄的耐候钢块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> YELLOWING_WEATHERING_STEEL_BLOCK = BlockInit.register("yellowing_weathering_steel_block",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.YELLOWING_WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //耐候钢锈块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> RUSTED_WEATHERING_STEEL_BLOCK = BlockInit.register("rusted_weathering_steel_block",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.RUSTED_WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //稳定的耐候钢锈块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> STABLY_RUSTED_WEATHERING_STEEL_BLOCK = BlockInit.register("stably_rusted_weathering_steel_block",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.STABLY_WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    //致密的耐候钢锈块
    public static final DeferredHolder<Block, FullWeatheringSteelBlock> FULLY_RUSTED_WEATHERING_STEEL_BLOCK = BlockInit.register("fully_rusted_weathering_steel_block",
            () -> new FullWeatheringSteelBlock(IWeatheringSteel.WSWeatherState.FULLY_WS,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f)
                    .sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    // 涂蜡的耐候钢块
    public static final DeferredHolder<Block, Block> WAXED_WEATHERING_STEEL_BLOCK = BlockInit.register("waxed_weathering_resistant_steel_block_0",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    // 涂蜡的斑驳耐候钢块
    public static final DeferredHolder<Block, Block> WAXED_EXPOSED_WEATHERING_STEEL_BLOCK = BlockInit.register("waxed_exposed_weathering_steel_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    // 涂蜡的泛黄耐候钢块
    public static final DeferredHolder<Block, Block> WAXED_YELLOWING_WEATHERING_STEEL_BLOCK = BlockInit.register("waxed_yellowing_weathering_steel_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    // 涂蜡的耐候钢锈块
    public static final DeferredHolder<Block, Block> WAXED_RUSTED_WEATHERING_STEEL_BLOCK = BlockInit.register("waxed_rusted_weathering_steel_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    // 涂蜡的稳定耐候钢锈块
    public static final DeferredHolder<Block, Block> WAXED_STABLY_RUSTED_WEATHERING_STEEL_BLOCK = BlockInit.register("waxed_stably_rusted_weathering_steel_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    // 涂蜡的致密耐候钢锈块
    public static final DeferredHolder<Block, Block> WAXED_FULLY_RUSTED_WEATHERING_STEEL_BLOCK = BlockInit.register("waxed_fully_rusted_weathering_steel_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 3.0f).sound(SoundType.COPPER).requiresCorrectToolForDrops()),
            object -> () -> new BlockItem(object.get(), new Item.Properties()));

    public static void invoke(){};
}
