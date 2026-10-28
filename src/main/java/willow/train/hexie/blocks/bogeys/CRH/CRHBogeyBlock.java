package willow.train.hexie.blocks.bogeys.CRH;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.world.level.block.entity.BlockEntityType;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.init.bogeys.BogeyEntityInit;
import willow.train.hexie.init.bogeys.BogeyStylesInit;

public class CRHBogeyBlock extends HexieBogeyBlock<CRHBogeyBlockEntity> {

    public CRHBogeyBlock(Properties properties, BogeySizes.BogeySize size) {
        super(properties, size);
    }

    @Override
    public BogeyStyle getDefaultStyle() {
        return BogeyStylesInit.CRHBOGEY;
    }

    @Override
    public Class<CRHBogeyBlockEntity> getBlockEntityClass() {
        return CRHBogeyBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CRHBogeyBlockEntity> getBlockEntityType() {
        return BogeyEntityInit.CRH_BOGEY.get();
    }
}
