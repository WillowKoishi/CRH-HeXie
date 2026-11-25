package willow.train.hexie.vehicle.bogey.registry;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.BogeyEntityFamily;
import willow.train.hexie.bridge.railway.TrackRegistryBridge.TrackBlockInitOperations;
import willow.train.hexie.core.HexieConstants;

@EventBusSubscriber(modid = HexieConstants.MOD_ID)
public final class BogeyEntityInit {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HexieConstants.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> HX_STANDARD_BOGEY =
            register("hx_standard_bogey", BogeyEntityFamily.STANDARD, () -> BogeyEntityInit.HX_STANDARD_BOGEY.get(), b -> new Block[] {
                    b.hxStandardBogey(), b.pk209pBogey(), b.pk209pNoMotorBogey(), b.pk209pBackwardBogey(),
                    b.sw220kBogey(), b.sw220kBackwardBogey(), b.sw160Bogey()});
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> DF11G_BOGEY =
            register("df11g_bogey", BogeyEntityFamily.DF11G, () -> BogeyEntityInit.DF11G_BOGEY.get(), b -> new Block[] {
                    b.df11gBogey(), b.df11gBackwardBogey(), b.qjBogey(), b.qjBackwardBogey()});
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> SS7D_BOGEY =
            register("ss7d_bogey", BogeyEntityFamily.SS7D, () -> BogeyEntityInit.SS7D_BOGEY.get(), b -> new Block[] {b.ss7dFrontBogey()});
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> CRH_BOGEY =
            register("crh_bogey", BogeyEntityFamily.CRH, () -> BogeyEntityInit.CRH_BOGEY.get(), b -> new Block[] {
                    b.dt206Bogey(), b.sktb200Bogey(), b.crh1aMotorBogey(), b.crh1aTrailerBogey()});

    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> register(String id,
            BogeyEntityFamily family, Supplier<BlockEntityType<BlockEntity>> type, Function<TrackBlockInitOperations, Block[]> blocks) {
        return BLOCK_ENTITIES.register(id, () -> {
            BlockEntityType.BlockEntitySupplier<BlockEntity> factory = ModList.get().isLoaded("create")
                    ? HexieBridges.createTrain().bogeyEntityFactory(family, type)
                    : (pos, state) -> HexieBridges.worldBlock().createStandaloneBogeyBlockEntity(type.get(), pos, state);
            return BlockEntityType.Builder.of(factory, blocks.apply(HexieBridges.trackRegistry().trackBlockInit())).build(null);
        });
    }

    private static boolean registered;

    public static synchronized void register(IEventBus bus) {
        if (registered) return;
        BLOCK_ENTITIES.register(bus);
        registered = true;
    }

    // The legacy entry point has no bus argument. Construction still precedes every RegisterEvent.
    @SubscribeEvent private static void onConstruct(FMLConstructModEvent event) {
        if (!registered) register(HexieBridges.modBootstrap().modBus());
    }

    public static void register() {}
}
