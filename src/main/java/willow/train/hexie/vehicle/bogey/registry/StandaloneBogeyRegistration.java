package willow.train.hexie.vehicle.bogey.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.api.bogey.BogeyDefinition;
import willow.train.hexie.core.api.bogey.BogeyHandle;
import willow.train.hexie.core.api.railway.BogeyRailwayRegistry;
import willow.train.hexie.vehicle.assembly.BogeyModelCatalog;

final class StandaloneBogeyRegistration {
    final BogeyHandle handle;
    private final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> entityType;

    StandaloneBogeyRegistration(IEventBus bus, ResourceLocation id, BogeyDefinition definition) {
        var owner = ModList.get().getModContainerById(id.getNamespace()).orElseThrow(() ->
                new IllegalArgumentException("Bogey namespace is not a loaded mod: " + id.getNamespace()));
        if (owner.getEventBus() != bus)
            throw new IllegalArgumentException("Use the owning mod's event bus to register its bogeys: " + id);
        String namespace = id.getNamespace();
        String path = definition.blockId().getPath();
        var blocks = DeferredRegister.createBlocks(namespace);
        var items = DeferredRegister.createItems(namespace);
        var entities = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, namespace);

        var block = blocks.register(path, () -> HexieBridges.worldBlock().createStandaloneBogeyBlock(
                BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).mapColor(MapColor.METAL)
                        .sound(SoundType.NETHERITE_BLOCK).noOcclusion(),
                definition.model().geometry(), this::entityType));
        var item = items.register(path, () -> new BlockItem(block.get(), new Item.Properties()));
        entityType = entities.register(path, () -> BlockEntityType.Builder.of(this::createEntity, block.get()).build(null));
        handle = new BogeyHandle(id, definition, block::get, item::get);
        blocks.register(bus);
        items.register(bus);
        entities.register(bus);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            BogeyModelCatalog.production().register(definition.blockId(), definition.model());
            BogeyRailwayRegistry.register(id, definition.allowedRailways());
        }));
    }

    private BlockEntityType<BlockEntity> entityType() { return entityType.get(); }

    private BlockEntity createEntity(BlockPos pos, BlockState state) {
        return HexieBridges.worldBlock().createStandaloneBogeyBlockEntity(entityType(), pos, state);
    }
}
