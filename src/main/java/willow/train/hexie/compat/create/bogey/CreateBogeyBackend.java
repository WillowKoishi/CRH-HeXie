package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.AllBogeyStyles;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.api.bogey.BogeyDefinition;
import willow.train.hexie.api.bogey.BogeyHandle;
import willow.train.hexie.api.railway.BogeyRailwayRegistry;
import willow.train.hexie.bogey.BogeyBackend;
import willow.train.hexie.compat.create.CreateBlockReservations;
import willow.train.hexie.vehicle.assembly.BogeyModelCatalog;

/** Creates the active backend's style/block/entity plumbing without exposing it to add-ons. */
public final class CreateBogeyBackend implements BogeyBackend {
    private static final Map<String, Registers> REGISTERS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Registration> ENTRIES = new LinkedHashMap<>();

    @Override public synchronized BogeyHandle register(IEventBus bus, ResourceLocation id, BogeyDefinition definition) {
        var owner = ModList.get().getModContainerById(id.getNamespace()).orElseThrow(() ->
                new IllegalArgumentException("Bogey namespace is not a loaded mod: " + id.getNamespace()));
        if (owner.getEventBus() != bus)
            throw new IllegalArgumentException("Use the owning mod's event bus to register its bogeys: " + id);
        if (!id.getNamespace().equals(definition.blockId().getNamespace()))
            throw new IllegalArgumentException("Bogey and block IDs must share their owning namespace");
        if (ENTRIES.containsKey(id) || AllBogeyStyles.BOGEY_STYLES.containsKey(id))
            throw new IllegalArgumentException("Bogey style already registered: " + id);
        CreateBlockReservations.reserve("bogey " + id, List.of(definition.blockId()));
        var registers = REGISTERS.computeIfAbsent(id.getNamespace(), namespace -> new Registers(namespace, bus));
        if (registers.bus != bus)
            throw new IllegalArgumentException("Bogeys in one namespace must use the same mod event bus: " + id);

        var entry = new Registration(id, definition);
        String path = definition.blockId().getPath();
        entry.block = registers.blocks.register(path, () -> new RegisteredBogeyBlock(
                BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).mapColor(MapColor.METAL)
                        .sound(SoundType.NETHERITE_BLOCK).noOcclusion(),
                id, () -> entry.style, () -> entry.entityType.get()));
        entry.item = registers.items.register(path, () -> new BlockItem(entry.block.get(), new Item.Properties()));
        entry.entityType = registers.entities.register(path,
                () -> BlockEntityType.Builder.of(RegisteredBogeyBlockEntity::new, entry.block.get()).build(null));
        entry.style = new BogeyStyle.Builder(id, id)
                .displayName(Component.translatable("block." + definition.blockId().getNamespace() + "."
                        + definition.blockId().getPath().replace('/', '.')))
                .size(BogeySizes.SMALL, entry.block, () -> () -> CreateAddonBogeyRenderer.create(id))
                .build();
        ENTRIES.put(id, entry);
        return new BogeyHandle(id, definition, entry.block::get, entry.item::get);
    }

    public static synchronized List<BlockEntityType<RegisteredBogeyBlockEntity>> blockEntityTypes() {
        return ENTRIES.values().stream().map(entry -> entry.entityType.get()).toList();
    }

    public static synchronized BogeyStyle style(ResourceLocation id) {
        var entry = ENTRIES.get(id);
        if (entry == null) throw new IllegalArgumentException("Unregistered add-on bogey: " + id);
        return entry.style;
    }

    private static final class Registers {
        final IEventBus bus;
        final DeferredRegister<Block> blocks;
        final DeferredRegister<Item> items;
        final DeferredRegister<BlockEntityType<?>> entities;

        Registers(String namespace, IEventBus bus) {
            this.bus = bus;
            blocks = DeferredRegister.create(Registries.BLOCK, namespace);
            items = DeferredRegister.create(Registries.ITEM, namespace);
            entities = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, namespace);
            blocks.register(bus);
            items.register(bus);
            entities.register(bus);
            bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> registerOperating(namespace)));
        }
    }

    private static synchronized void registerOperating(String namespace) {
        for (var entry : ENTRIES.values()) {
            if (!entry.id.getNamespace().equals(namespace)) continue;
            BogeyModelCatalog.production().register(entry.definition.blockId(), entry.definition.model());
            BogeyRailwayRegistry.register(entry.id, entry.definition.allowedRailways());
        }
    }

    private static final class Registration {
        final ResourceLocation id;
        final BogeyDefinition definition;
        DeferredHolder<Block, RegisteredBogeyBlock> block;
        DeferredHolder<Item, BlockItem> item;
        DeferredHolder<BlockEntityType<?>, BlockEntityType<RegisteredBogeyBlockEntity>> entityType;
        BogeyStyle style;
        Registration(ResourceLocation id, BogeyDefinition definition) { this.id = id; this.definition = definition; }
    }
}
