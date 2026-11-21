package willow.train.hexie.blocks.bogeys.ss7d;

import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.world.level.block.entity.BlockEntityType;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.init.bogeys.BogeyEntityInit;
import willow.train.hexie.init.bogeys.BogeySizesInit;
import willow.train.hexie.init.bogeys.BogeyStylesInit;
import willow.train.hexie.vehicle.bogey.BuiltinBogeyProfile;

public final class SS7DBogeyBlock extends HexieBogeyBlock<SS7DBogeyBlockEntity> {
    public SS7DBogeyBlock(Properties properties) {
        super(properties, BogeySizesInit.SS7D_FRONT, BuiltinBogeyProfile.SS7D_FRONT.geometry().axleSpan(),
                BuiltinBogeyProfile.SS7D_FRONT.geometry().wheelRadius());
    }

    @Override public BogeyStyle getDefaultStyle() { return BogeyStylesInit.LOCOBOGEY; }
    @Override public Class<SS7DBogeyBlockEntity> getBlockEntityClass() { return SS7DBogeyBlockEntity.class; }
    @Override public BlockEntityType<? extends SS7DBogeyBlockEntity> getBlockEntityType() {
        return BogeyEntityInit.SS7D_BOGEY.get();
    }
}
