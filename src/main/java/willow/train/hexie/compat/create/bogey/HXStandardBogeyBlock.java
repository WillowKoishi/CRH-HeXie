package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import willow.train.hexie.bridge.HexieBridges;

public class HXStandardBogeyBlock extends HexieBogeyBlock<HXStandardBogeyBlockEntity> {
    public HXStandardBogeyBlock(BlockBehaviour.Properties properties, BogeySizes.BogeySize size) {
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
    @SuppressWarnings("unchecked")
    public BlockEntityType<? extends HXStandardBogeyBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends HXStandardBogeyBlockEntity>) HexieBridges.bogeyRegistry().bogeyEntityInit().hxStandardBogey();
    }
}
