package willow.train.hexie.init.semaphore;


import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

import willow.train.hexie.item.semaphore.ThroughSignalItem;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

import willow.train.hexie.Main;
import willow.train.hexie.blocks.semaphore.through.ThroughSignalBlock;

public class SemaphoreBlockInit {

    private static final CreateRegistrate REGISTRATE =
            Main.registrate().setCreativeTab(Main.HEXIE_MAIN.holder());


    public static final BlockEntry<ThroughSignalBlock> HX_SEMAPHORE = REGISTRATE.block("hx_semaphore", ThroughSignalBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.mapColor(MapColor.PODZOL))
            .properties(p -> p.noOcclusion())
            .properties(p -> p.sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .blockstate((c, p) -> p.getVariantBuilder(c.get())
                    .forAllStates(state -> ConfiguredModel.builder()
                            .modelFile(p.models().getExistingFile(p.modLoc("block/hx_semaphore/hx_shunting_signal")))
                            .rotationY((state.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue() * 90 + 180) % 360)
                            .build()))
            .lang("Color Light Signal")
            .item(ThroughSignalItem::new)
            .model((c, p) -> p.withExistingParent(c.getName(), Main.asResource("block/hx_semaphore/hx_shunting_signal")))
            .build()
            .register();

    public static void register() {}
}
