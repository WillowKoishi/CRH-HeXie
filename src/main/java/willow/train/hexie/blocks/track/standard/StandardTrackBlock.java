package willow.train.hexie.blocks.track.standard;

import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
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
        Direction.Axis axis = switch (state.getValue(SHAPE)) {
            case XO -> Direction.Axis.X;
            default -> Direction.Axis.Z;
        };
        BlockState bogey = TrackBlockInit.HX_STANDARD_BOGEY.getDefaultState();
        return bogey.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
    }
}
