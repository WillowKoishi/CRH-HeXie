package willow.train.hexie.init.semaphore;


import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

import willow.train.hexie.item.semaphore.ThroughSignalItem;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.ModelFile;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

import willow.train.hexie.Main;
import willow.train.hexie.blocks.semaphore.through.ThroughSignalBlock;
import willow.train.hexie.blocks.semaphore.through.AdjustableSignalBlock;

public class SemaphoreBlockInit {

    private static final CreateRegistrate REGISTRATE =
            Main.registrate().setCreativeTab(Main.HEXIE_MAIN.holder());


    public static final BlockEntry<ThroughSignalBlock> HX_SEMAPHORE = REGISTRATE.block("hx_semaphore", ThroughSignalBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.PODZOL))
            .properties(p -> p.noOcclusion())
            .properties(p -> p.sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .blockstate(SemaphoreBlockInit::generateBlockstate)
            .lang("Color Light Signal")
            .item(ThroughSignalItem::new)
            .model((c, p) -> p.withExistingParent(c.getName(), Main.asResource("block/hx_semaphore/hx_shunting_signal")))
            .build()
            .register();

    public static final BlockEntry<AdjustableSignalBlock> ADJUSTABLE_SEMAPHORE = REGISTRATE
            .block("adjustable_semaphore", AdjustableSignalBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(properties -> properties.mapColor(MapColor.PODZOL).noOcclusion()
                    .sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .blockstate((context, provider) -> provider.simpleBlock(context.get(),
                    provider.models().getExistingFile(provider.modLoc("block/adjustable_semaphore"))))
            .lang("Adjustable Color Light Signal")
            .item(ThroughSignalItem::new)
            .model((context, provider) -> provider.withExistingParent(context.getName(),
                    Main.asResource("block/hx_semaphore/hx_shunting_signal")))
            .build()
            .register();

    private static void generateBlockstate(
            DataGenContext<Block, ThroughSignalBlock> context, RegistrateBlockstateProvider provider) {
        ModelFile housing = provider.models().getExistingFile(
                provider.modLoc("block/hx_semaphore/hx_shunting_signal"));
        provider.getVariantBuilder(context.get()).forAllStates(state -> {
            int quarterTurns = state.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue();
            return ConfiguredModel.builder().modelFile(housing)
                    .rotationY((quarterTurns * 90 + 180) % 360).build();
        });
    }

    public static void register() {}
}
