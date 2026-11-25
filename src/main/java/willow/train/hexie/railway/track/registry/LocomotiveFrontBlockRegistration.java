package willow.train.hexie.railway.track.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.core.HexieConstants;

public final class LocomotiveFrontBlockRegistration {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, HexieConstants.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, HexieConstants.MOD_ID);

    public static final DeferredHolder<Block, Block> DF11G_FRONT_BLOCK = registerFront("df11g_front2");
    public static final DeferredHolder<Block, Block> HXD3D_FRONT_BLOCK = registerFront("hxd3d_front");

    private static DeferredHolder<Block, Block> registerFront(String id) {
        DeferredHolder<Block, Block> block = BLOCKS.register(id, () -> {
            var properties = BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK).mapColor(MapColor.COLOR_BLUE).noOcclusion();
            return HexieBridges.carriageBlock().createDF11GFrontBlock(properties);
        });
        ITEMS.register(id, () -> HexieBridges.itemFactory().createToolTipsItemHelper(block.get(), new Item.Properties()));
        return block;
    }

    private static boolean registered;

    public static synchronized void register(IEventBus bus) {
        if (registered) return;
        BLOCKS.register(bus);
        ITEMS.register(bus);
        registered = true;
    }

}
