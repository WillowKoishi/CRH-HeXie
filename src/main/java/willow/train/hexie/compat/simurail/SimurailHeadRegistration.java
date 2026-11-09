package willow.train.hexie.compat.simurail;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.Main;
import willow.train.hexie.item.CRH2HeadItem;

/** Loaded during registration only when Simurail and its required physics dependencies are present. */
public final class SimurailHeadRegistration {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Main.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Main.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Main.MOD_ID);

    public static final DeferredHolder<Block, PhysicalHeadBlock> PHYSICAL_HEAD = BLOCKS.register("crh2a_physical_head",
            () -> new PhysicalHeadBlock(properties()));
    public static final DeferredHolder<Block, PhysicalHeadBogeyBlock> PHYSICAL_HEAD_BOGEY = BLOCKS.register("crh2a_physical_head_bogey",
            () -> new PhysicalHeadBogeyBlock(properties().noLootTable()));
    public static final DeferredHolder<Item, CRH2HeadItem> PHYSICAL_HEAD_ITEM = ITEMS.register("crh2a_physical_head",
            () -> new CRH2HeadItem(PHYSICAL_HEAD.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PhysicsBogeyBlockEntity>> PHYSICAL_HEAD_BOGEY_ENTITY =
            ENTITIES.register("crh2a_physical_head_bogey", () -> BlockEntityType.Builder.<PhysicsBogeyBlockEntity>of(
                    PhysicalHeadBogeyBlockEntity::new, PHYSICAL_HEAD_BOGEY.get()).build(null));

    private SimurailHeadRegistration() {}

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2).sound(SoundType.METAL).noOcclusion();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus);
        bus.addListener(SimurailHeadRegistration::capabilities);
        bus.addListener(SimurailHeadRegistration::setup);
    }

    private static void setup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("simulated")) event.enqueueWork(PhysicalHeadAssembly::register);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        if (ModList.get().isLoaded("computercraft")) PhysicalHeadComputerSupport.register(event);
    }
}
