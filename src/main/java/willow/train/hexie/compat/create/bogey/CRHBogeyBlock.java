package willow.train.hexie.compat.create.bogey;

import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.WorldBlockBridge;
import willow.train.hexie.core.api.bogey.BogeyGeometry;

public class CRHBogeyBlock extends HexieBogeyBlock<CRHBogeyBlockEntity> implements WorldBlockBridge.CRHBogeyBlockView {

    public CRHBogeyBlock(BlockBehaviour.Properties properties, BogeySizes.BogeySize size) {
        super(properties, size);
    }

    public CRHBogeyBlock(BlockBehaviour.Properties properties, BogeySizes.BogeySize size, BogeyGeometry geometry) {
        super(properties, size, geometry.axleSpan(), geometry.wheelRadius());
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
    @SuppressWarnings("unchecked")
    public BlockEntityType<? extends CRHBogeyBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends CRHBogeyBlockEntity>) HexieBridges.bogeyRegistry().bogeyEntityInit().crhBogey();
    }
}
