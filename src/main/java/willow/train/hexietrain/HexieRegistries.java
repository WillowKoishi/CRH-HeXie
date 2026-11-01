package willow.train.hexietrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 注册表。
 *
 * <p>方块 / 物品走 NeoForge 原生 {@link DeferredRegister}。
 *
 * <p>方块实体类型走<b>运行时反射</b>：因为对着运行时 jar 编译时，
 * {@code BlockEntityType.Builder.of} 的函数式接口 {@code BlockEntitySupplier}
 * 是包内可见的，编译期拿不到；运行时它其实可用（Create 的 Registrate 也是这么调的）。
 */
public final class HexieRegistries {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HexieTrain.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HexieTrain.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HexieTrain.MOD_ID);

    /** 列车调度台。 */
    public static final DeferredBlock<TrainDispatcherBlock> TRAIN_DISPATCHER =
            BLOCKS.register("train_dispatcher",
                    () -> new TrainDispatcherBlock(BlockBehaviour.Properties.of()
                            .strength(3.0f, 6.0f)
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> TRAIN_DISPATCHER_ITEM =
            ITEMS.registerSimpleBlockItem("train_dispatcher", TRAIN_DISPATCHER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrainDispatcherBlockEntity>> TRAIN_DISPATCHER_BE =
            BLOCK_ENTITIES.register("train_dispatcher",
                    () -> buildBlockEntityType(TRAIN_DISPATCHER.get()));

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, HexieTrain.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<TrainDispatcherMenu>> TRAIN_DISPATCHER_MENU =
            MENUS.register("train_dispatcher", () -> IMenuTypeExtension.create(TrainDispatcherMenu::new));

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HexieTrain.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("main", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.hexietrain"))
                    .icon(() -> new ItemStack(TRAIN_DISPATCHER_ITEM.get()))
                    .displayItems((params, output) -> output.accept(TRAIN_DISPATCHER_ITEM.get()))
                    .build());

    private HexieRegistries() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
    }

    // ══════════════ 反射构造 BlockEntityType ══════════════

    @SuppressWarnings("unchecked")
    private static BlockEntityType<TrainDispatcherBlockEntity> buildBlockEntityType(Block block) {
        try {
            Class<?> supplierType = Class.forName(
                    "net.minecraft.world.level.block.entity.BlockEntityType$BlockEntitySupplier");

            Object supplier = Proxy.newProxyInstance(
                    supplierType.getClassLoader(),
                    new Class<?>[]{supplierType},
                    (proxy, method, args) -> {
                        if (args == null || args.length < 2) return null;
                        return new TrainDispatcherBlockEntity(
                                (BlockPos) args[0], (BlockState) args[1]);
                    });

            Method of = BlockEntityType.Builder.class
                    .getMethod("of", supplierType, Block[].class);
            Object builder = of.invoke(null, supplier, (Object) new Block[]{block});

            Method build = builder.getClass()
                    .getMethod("build", com.mojang.datafixers.types.Type.class);
            return (BlockEntityType<TrainDispatcherBlockEntity>) build.invoke(builder, (Object) null);

        } catch (Throwable t) {
            throw new RuntimeException("[HexieTrain] 构造 BlockEntityType 失败", t);
        }
    }
}