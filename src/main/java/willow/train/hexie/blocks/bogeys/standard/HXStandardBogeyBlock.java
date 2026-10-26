package willow.train.hexie.blocks.bogeys.standard;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.world.level.block.entity.BlockEntityType;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.init.bogeys.BogeyEntityInit;
import willow.train.hexie.init.bogeys.BogeyStylesInit;

public class HXStandardBogeyBlock extends HexieBogeyBlock<HXStandardBogeyBlockEntity> {
    public HXStandardBogeyBlock(Properties properties, BogeySizes.BogeySize size) {
        super(properties, size);
    }

    @Override
    public BogeyStyle getDefaultStyle() {
        return BogeyStylesInit.HXSTANDARDBOGEY;
    }

    @Override
    public Class<HXStandardBogeyBlockEntity> getBlockEntityClass() {
        return HXStandardBogeyBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HXStandardBogeyBlockEntity> getBlockEntityType() {
        return BogeyEntityInit.HX_STANDARD_BOGEY.get();
    }
}
