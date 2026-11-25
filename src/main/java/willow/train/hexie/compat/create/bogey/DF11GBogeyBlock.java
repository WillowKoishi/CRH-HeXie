package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import willow.train.hexie.bridge.HexieBridges;

public class DF11GBogeyBlock extends HexieBogeyBlock<DF11GBogeyBlockEntity> {
    public DF11GBogeyBlock(BlockBehaviour.Properties properties, BogeySizes.BogeySize size) {
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
    @SuppressWarnings("unchecked")
    public BlockEntityType<? extends DF11GBogeyBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends DF11GBogeyBlockEntity>) HexieBridges.bogeyRegistry().bogeyEntityInit().df11gBogey();
    }
}
