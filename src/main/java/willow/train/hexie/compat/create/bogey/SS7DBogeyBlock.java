package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.vehicle.BogeyRegistryBridge;

public final class SS7DBogeyBlock extends HexieBogeyBlock<SS7DBogeyBlockEntity> {
    public SS7DBogeyBlock(BlockBehaviour.Properties properties) {
        super(properties, BogeySizesInit.SS7D_FRONT, HexieBridges.bogeyRegistry().builtinBogeyProfile().geometry(BogeyRegistryBridge.BuiltinBogeyProfile.SS7D_FRONT).axleSpan(),
                HexieBridges.bogeyRegistry().builtinBogeyProfile().geometry(BogeyRegistryBridge.BuiltinBogeyProfile.SS7D_FRONT).wheelRadius());
    }

    @Override public BogeyStyle getDefaultStyle() { return BogeyStylesInit.LOCOBOGEY; }
    @Override public Class<SS7DBogeyBlockEntity> getBlockEntityClass() { return SS7DBogeyBlockEntity.class; }
    @Override @SuppressWarnings("unchecked")
    public BlockEntityType<? extends SS7DBogeyBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends SS7DBogeyBlockEntity>) HexieBridges.bogeyRegistry().bogeyEntityInit().ss7dBogey();
    }
}
