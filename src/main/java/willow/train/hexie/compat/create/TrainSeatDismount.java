package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import willow.train.hexie.blocks.locos.CRH2DrivingConsoleBlock;
import net.minecraft.world.phys.Vec3;

/** Exit one block behind the operation point, away from its driving console. */
public final class TrainSeatDismount {
    private TrainSeatDismount() {}

    public static Vec3 behind(AbstractContraptionEntity entity, BlockPos seat) {
        Vec3 feet = localBehind(entity, seat);
        if (feet == null) return null;
        return entity instanceof SnapshotCarriageEntity car
                ? CarriagePlayerSupport.frame(car, 1).world(feet)
                : entity.toGlobalVector(feet, 1);
    }

    public static Vec3 localBehind(AbstractContraptionEntity entity, BlockPos seat) {
        var world = entity.getContraption().getContraptionWorld();
        for (var facing : Direction.Plane.HORIZONTAL) {
            var console = world.getBlockState(seat.relative(facing));
            if (console.getBlock() instanceof CRH2DrivingConsoleBlock
                    && console.getValue(CRH2DrivingConsoleBlock.FACING) == facing) {
                return Vec3.atBottomCenterOf(seat.relative(facing.getOpposite()));
            }
        }
        return null;
    }
}
