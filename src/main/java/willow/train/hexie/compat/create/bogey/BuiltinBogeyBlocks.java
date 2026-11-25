package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreateTrainBridgePort.BogeyEntityFamily;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge.BuiltinBogeyModel;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge.BuiltinBogeyProfile;

public final class BuiltinBogeyBlocks {
    private BuiltinBogeyBlocks() {}

    public static Block create(BlockBehaviour.Properties properties, BuiltinBogeyModel model) {
        var bogeys = HexieBridges.bogeyRegistry();
        return switch (model) {
            case CW2 -> new HXStandardBogeyBlock(properties, BogeySizes.SMALL);
            case DF11G -> new DF11GBogeyBlock(properties, BogeySizes.SMALL);
            case SS7D_FRONT -> new SS7DBogeyBlock(properties);
            case DF11G_BACKWARD -> new DF11GBogeyBlock(properties, BogeySizesInit.BACKWARD);
            case PK209P -> new HXStandardBogeyBlock(properties, BogeySizesInit.PK209P);
            case PK209P_NO_MOTOR -> new HXStandardBogeyBlock(properties, BogeySizesInit.PK209P_NO_MOTOR);
            case PK209P_BACKWARD -> new HXStandardBogeyBlock(properties, BogeySizesInit.PK209P_BACKWARD);
            case SW220K -> new HXStandardBogeyBlock(properties, BogeySizesInit.SW220K);
            case SW220K_BACKWARD -> new HXStandardBogeyBlock(properties, BogeySizesInit.SW220K_BACKWARD);
            case QJ -> new DF11GBogeyBlock(properties, BogeySizesInit.QJ);
            case QJ_BACKWARD -> new DF11GBogeyBlock(properties, BogeySizesInit.QJ_BACKWARD);
            case SW160 -> new HXStandardBogeyBlock(properties, BogeySizesInit.SW160);
            case DT206 -> new CRHBogeyBlock(properties, BogeySizesInit.DT206);
            case SKTB200 -> new CRHBogeyBlock(properties, BogeySizesInit.SKTB200);
            case CRH1A_MOTOR -> new CRHBogeyBlock(properties, BogeySizesInit.CRH1A_MOTOR,
                    bogeys.builtinBogeyProfile().geometry(BuiltinBogeyProfile.CRH1A_MOTOR));
            case CRH1A_TRAILER -> new CRHBogeyBlock(properties, BogeySizesInit.CRH1A_TRAILER,
                    bogeys.builtinBogeyProfile().geometry(BuiltinBogeyProfile.CRH1A_TRAILER));
        };
    }

    public static BlockEntityType.BlockEntitySupplier<BlockEntity> entityFactory(BogeyEntityFamily family,
            Supplier<BlockEntityType<BlockEntity>> type) {
        return switch (family) {
            case STANDARD -> (pos, state) -> new HXStandardBogeyBlockEntity(type.get(), pos, state);
            case DF11G -> (pos, state) -> new DF11GBogeyBlockEntity(type.get(), pos, state);
            case SS7D -> (pos, state) -> new SS7DBogeyBlockEntity(type.get(), pos, state);
            case CRH -> (pos, state) -> new CRHBogeyBlockEntity(type.get(), pos, state);
        };
    }
}
