package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Fixed ground-level exit one block behind a directional seat. */
public final class TrainSeatDismount {
    private TrainSeatDismount() {}

    public static Vec3 behind(AbstractContraptionEntity entity, BlockPos seat) {
        var state = entity.getContraption().getBlocks().get(seat).state();
        // Undirected Create cushions have no authored back; retain their native exit.
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return null;
        BlockPos rear = seat.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        Vec3 feet = Vec3.atBottomCenterOf(rear);
        return entity instanceof SnapshotCarriageEntity car
                ? CarriagePlayerSupport.frame(car, 1).world(feet)
                : entity.toGlobalVector(feet, 1);
    }
}
