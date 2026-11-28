package willow.train.hexie.world.registry.blocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.world.blocks.carriage.Carriage25GFloorBlock;
import willow.train.hexie.world.blocks.carriage.Carriage25GPanelBlock;
import willow.train.hexie.world.blocks.parts.PartDefinitions;

/** The carriage25 pack owns registration as well as its block and item resources. */
@EventBusSubscriber(modid = HexieConstants.MOD_ID)
public final class Carriage25Blocks {
    public static final DeferredHolder<Block, Carriage25GFloorBlock> FLOOR_DC600 = DeferredHolder.create(
            Registries.BLOCK, HexieConstants.asResource("carriage25g_floor_dc600"));
    public static final DeferredHolder<Block, Carriage25GPanelBlock> PANEL_BOTTOM = DeferredHolder.create(
            Registries.BLOCK, HexieConstants.asResource("carriage25g_panel_bottom"));
    public static final DeferredHolder<Block, Carriage25GPanelBlock> PANEL_UPPER = DeferredHolder.create(
            Registries.BLOCK, HexieConstants.asResource("carriage25g_panel_upper"));

    private Carriage25Blocks() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PartDefinitions.register(FLOOR_DC600.getId(),
                    new PartDefinitions.Definition(PartDefinitions.Extension.FLOOR, false, false));
            PartDefinitions.register(PANEL_BOTTOM.getId(),
                    new PartDefinitions.Definition(PartDefinitions.Extension.SIDE, false, false));
            PartDefinitions.register(PANEL_UPPER.getId(),
                    new PartDefinitions.Definition(PartDefinitions.Extension.SIDE, false, false));
        });
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> helper.register(FLOOR_DC600.getId(),
                new Carriage25GFloorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(2).sound(SoundType.METAL).noOcclusion())));
        event.register(Registries.ITEM, helper -> helper.register(FLOOR_DC600.getId(),
                HexieBridges.itemFactory().createPartBlockItem(FLOOR_DC600.get(), new Item.Properties())));
        for (var panel : java.util.List.of(PANEL_BOTTOM, PANEL_UPPER)) {
            event.register(Registries.BLOCK, helper -> helper.register(panel.getId(),
                    new Carriage25GPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                            .strength(2).sound(SoundType.METAL).noOcclusion(), panel.getId().getPath())));
            event.register(Registries.ITEM, helper -> helper.register(panel.getId(),
                    HexieBridges.itemFactory().createCRH2PartItem(panel.get(), new Item.Properties())));
        }
    }
}
