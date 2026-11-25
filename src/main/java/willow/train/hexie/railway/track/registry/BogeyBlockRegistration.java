package willow.train.hexie.railway.track.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge.BuiltinBogeyModel;
import willow.train.hexie.core.HexieConstants;

public final class BogeyBlockRegistration {
    public static final DeferredRegister<Block> BOGEY_BLOCKS =
            DeferredRegister.create(Registries.BLOCK, HexieConstants.MOD_ID);

    public static final DeferredHolder<Block, Block> HX_STANDARD_BOGEY =
            register("hx_standard_bogey", MapColor.PODZOL, BuiltinBogeyModel.CW2);
    public static final DeferredHolder<Block, Block> DF11G_BOGEY =
            register("df11g_bogey", MapColor.PODZOL, BuiltinBogeyModel.DF11G);
    public static final DeferredHolder<Block, Block> SS7D_FRONT_BOGEY =
            register("ss7d_front_bogey", MapColor.METAL, BuiltinBogeyModel.SS7D_FRONT);
    public static final DeferredHolder<Block, Block> DF11G_BACKWARD_BOGEY =
            register("df11g_backward_bogey", MapColor.PODZOL, BuiltinBogeyModel.DF11G_BACKWARD);
    public static final DeferredHolder<Block, Block> PK209P_BOGEY =
            register("pk209p_bogey", MapColor.PODZOL, BuiltinBogeyModel.PK209P);
    public static final DeferredHolder<Block, Block> PK209P_NO_MOTOR_BOGEY =
            register("pk209p_no_motor_bogey", MapColor.PODZOL, BuiltinBogeyModel.PK209P_NO_MOTOR);
    public static final DeferredHolder<Block, Block> PK209P_BACKWARD_BOGEY =
            register("pk209p_backward_bogey", MapColor.PODZOL, BuiltinBogeyModel.PK209P_BACKWARD);
    public static final DeferredHolder<Block, Block> SW220K_BOGEY =
            register("sw220k_bogey", MapColor.PODZOL, BuiltinBogeyModel.SW220K);
    public static final DeferredHolder<Block, Block> SW220K_BACKWARD_BOGEY =
            register("sw220k_backward_bogey", MapColor.PODZOL, BuiltinBogeyModel.SW220K_BACKWARD);
    public static final DeferredHolder<Block, Block> QJ_BOGEY =
            register("qj_bogey", MapColor.PODZOL, BuiltinBogeyModel.QJ);
    public static final DeferredHolder<Block, Block> QJ_BACKWARD_BOGEY =
            register("qj_backward_bogey", MapColor.PODZOL, BuiltinBogeyModel.QJ_BACKWARD);
    public static final DeferredHolder<Block, Block> SW160_BOGEY =
            register("sw160_bogey", MapColor.PODZOL, BuiltinBogeyModel.SW160);
    public static final DeferredHolder<Block, Block> DT206_BOGEY =
            register("dt206_bogey", MapColor.METAL, BuiltinBogeyModel.DT206);
    public static final DeferredHolder<Block, Block> SKTB200_BOGEY =
            register("sktb200_bogey", MapColor.METAL, BuiltinBogeyModel.SKTB200);
    public static final DeferredHolder<Block, Block> CRH1A_MOTOR_BOGEY =
            register("crh1a_motor_bogey", MapColor.METAL, BuiltinBogeyModel.CRH1A_MOTOR);
    public static final DeferredHolder<Block, Block> CRH1A_TRAILER_BOGEY =
            register("crh1a_trailer_bogey", MapColor.METAL, BuiltinBogeyModel.CRH1A_TRAILER);

    private static DeferredHolder<Block, Block> register(String id, MapColor color, BuiltinBogeyModel model) {
        return BOGEY_BLOCKS.register(id, () -> {
            var properties = BlockBehaviour.Properties.of().mapColor(color).strength(4.0F, 6.0F)
                    .requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK).noOcclusion();
            return ModList.get().isLoaded("create") ? HexieBridges.createTrain().createBuiltinBogeyBlock(properties, model)
                    : HexieBridges.worldBlock().createStandaloneBogeyBlock(properties, model);
        });
    }

    private static boolean registered;

    public static synchronized void register(IEventBus bus) {
        if (registered) return;
        BOGEY_BLOCKS.register(bus);
        registered = true;
    }

}
