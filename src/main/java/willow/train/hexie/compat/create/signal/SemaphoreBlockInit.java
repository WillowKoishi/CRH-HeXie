package willow.train.hexie.compat.create.signal;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.compat.create.CreateBootstrap;
import willow.train.hexie.core.HexieConstants;

public class SemaphoreBlockInit {

    public static BlockEntry<HorizontalDirectionalBlock> HX_SEMAPHORE;
    public static BlockEntry<HorizontalDirectionalBlock> ADJUSTABLE_SEMAPHORE;

    public static Block hxSemaphore() {
        return HX_SEMAPHORE == null ? null : HX_SEMAPHORE.get();
    }

    public static Block adjustableSemaphore() {
        return ADJUSTABLE_SEMAPHORE == null ? null : ADJUSTABLE_SEMAPHORE.get();
    }

    public static void register() {
        if (ModList.get().isLoaded("create")) CreateSignals.register();
    }

    private static final class CreateSignals {
        private static final CreateRegistrate REGISTRATE =
                CreateBootstrap.registrate().setCreativeTab(HexieBridges.modBootstrap().hexieMain().holder());

        static {
            HX_SEMAPHORE = REGISTRATE.<HorizontalDirectionalBlock>block("create_hx_semaphore", ThroughSignalBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(p -> p.mapColor(MapColor.PODZOL))
                    .properties(p -> p.noOcclusion())
                    .properties(p -> p.sound(SoundType.NETHERITE_BLOCK))
                    .setData(ProviderType.BLOCK_TAGS, (c, p) ->
                            p.addTag(BlockTags.MINEABLE_WITH_PICKAXE).addOptional(c.getId()))
                    .blockstate(CreateSignals::generateBlockstate)
                    .lang("Create Color Light Signal")
                    .onRegister(block -> BuiltInRegistries.BLOCK.addAlias(
                            HexieConstants.asResource("hx_semaphore"), HexieConstants.asResource("create_hx_semaphore")))
                    .item(ThroughSignalItem::new)
                    .onRegister(item -> BuiltInRegistries.ITEM.addAlias(
                            HexieConstants.asResource("hx_semaphore"), HexieConstants.asResource("create_hx_semaphore")))
                    .model((c, p) -> p.withExistingParent(c.getName(), HexieConstants.asResource("block/hx_semaphore/hx_shunting_signal")))
                    .build()
                    .register();

            ADJUSTABLE_SEMAPHORE = REGISTRATE
                    .<HorizontalDirectionalBlock>block("create_adjustable_semaphore", AdjustableSignalBlock::new)
                    .initialProperties(SharedProperties::softMetal)
                    .properties(properties -> properties.mapColor(MapColor.PODZOL).noOcclusion()
                            .sound(SoundType.NETHERITE_BLOCK))
                    .setData(ProviderType.BLOCK_TAGS, (c, p) ->
                            p.addTag(BlockTags.MINEABLE_WITH_PICKAXE).addOptional(c.getId()))
                    .blockstate((context, provider) -> provider.simpleBlock(context.get(),
                            provider.models().getExistingFile(provider.modLoc("block/adjustable_semaphore"))))
                    .lang("Create Adjustable Color Light Signal")
                    .onRegister(block -> BuiltInRegistries.BLOCK.addAlias(
                            HexieConstants.asResource("adjustable_semaphore"), HexieConstants.asResource("create_adjustable_semaphore")))
                    .item(ThroughSignalItem::new)
                    .onRegister(item -> BuiltInRegistries.ITEM.addAlias(
                            HexieConstants.asResource("adjustable_semaphore"), HexieConstants.asResource("create_adjustable_semaphore")))
                    .model((context, provider) -> provider.withExistingParent(context.getName(),
                            HexieConstants.asResource("block/hx_semaphore/hx_shunting_signal")))
                    .build()
                    .register();
        }

        private static void generateBlockstate(
                DataGenContext<Block, HorizontalDirectionalBlock> context, RegistrateBlockstateProvider provider) {
            ModelFile housing = provider.models().getExistingFile(
                    provider.modLoc("block/hx_semaphore/hx_shunting_signal"));
            provider.getVariantBuilder(context.get()).forAllStates(state -> {
                int quarterTurns = state.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue();
                return ConfiguredModel.builder().modelFile(housing)
                        .rotationY((quarterTurns * 90 + 180) % 360).build();
            });
        }

        private static void register() {}
    }
}
