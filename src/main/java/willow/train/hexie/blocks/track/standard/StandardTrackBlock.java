package willow.train.hexie.blocks.track.standard;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import willow.train.hexie.init.track.TrackBlockInit;

public class StandardTrackBlock extends TrackBlock {

    public StandardTrackBlock(Properties properties, TrackMaterial material) {
        super(properties, material);
    }

    @Override
    public BlockState getBogeyAnchor(BlockGetter world, BlockPos pos, BlockState state) {
        return TrackBlockInit.HX_STANDARD_BOGEY.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS,
                        state.getValue(SHAPE) == TrackShape.XO ? Direction.Axis.X : Direction.Axis.Z);
    }
}
