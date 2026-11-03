package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
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
        var facing = willow.train.hexie.blocks.locos.DrivingConsoleContext.consoleFacing(seat, world::getBlockState);
        return facing == null ? null : Vec3.atBottomCenterOf(seat.relative(facing.getOpposite()));
    }
}
