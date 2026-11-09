package willow.train.hexie.blocks.bogeys.df11g;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.world.level.block.entity.BlockEntityType;
import willow.train.hexie.blocks.bogeys.HexieBogeyBlock;
import willow.train.hexie.init.bogeys.BogeyEntityInit;
import willow.train.hexie.init.bogeys.BogeySizesInit;
import willow.train.hexie.init.bogeys.BogeyStylesInit;

public class DF11GBogeyBlock extends HexieBogeyBlock<DF11GBogeyBlockEntity> {
    public DF11GBogeyBlock(Properties properties, BogeySizes.BogeySize size) {
        super(properties, size);
    }

    @Override
    public BogeyStyle getDefaultStyle() {
        return BogeySizesInit.QJ.equals(getSize()) || BogeySizesInit.QJ_BACKWARD.equals(getSize())
                ? BogeyStylesInit.STEAMBOGEY : BogeyStylesInit.LOCOBOGEY;
    }

    @Override
    public Class<DF11GBogeyBlockEntity> getBlockEntityClass() {
        return DF11GBogeyBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DF11GBogeyBlockEntity> getBlockEntityType() {
        return BogeyEntityInit.DF11G_BOGEY.get();
    }
}
