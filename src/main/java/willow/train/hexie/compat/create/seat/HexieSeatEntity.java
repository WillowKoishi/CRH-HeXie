package willow.train.hexie.compat.create.seat;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DriverSeatBlockView;
import willow.train.hexie.bridge.world.WorldBlockBridge;

public class HexieSeatEntity extends SeatEntity {

    public HexieSeatEntity(EntityType<?> p_i48580_1_, Level p_i48580_2_) {
        super(p_i48580_1_, p_i48580_2_);
    }

    public HexieSeatEntity(Level world) {
        this(HexieBridges.worldRegistry().entityInit().yzSeatEntity(), world);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        var pos = blockPosition();
        var state = level().getBlockState(pos);
        if (state.getBlock() instanceof CRH2DriverSeatBlockView) {
            var eye = HexieBridges.carriageBlock().crh2DriverSeatBlock().driverEyePosition(state);
            move.accept(passenger, pos.getX() + eye.x, pos.getY() + eye.y - passenger.getEyeHeight(), pos.getZ() + eye.z);
        } else if (state.getBlock() instanceof WorldBlockBridge.RZSeatBlockView) {
            move.accept(passenger, pos.getX() + .5, pos.getY() + WorldBlockBridge.RZSeatBlockOperations.EYE_HEIGHT
                    - passenger.getEyeHeight(), pos.getZ() + .5);
        } else {
            move.accept(passenger, getX(), getY() + WorldBlockBridge.SeatYZBlockOperations.EYE_HEIGHT
                    - passenger.getEyeHeight(), getZ());
        }
    }

    @Override
    protected void removePassenger(Entity entity) {
        super.removePassenger(entity);
        Set<String> tags = entity.getTags();
        for (String t : tags) {
            if (t.startsWith("sitting_on_yz_seat")) {
                return;
            }
        }
    }
}
