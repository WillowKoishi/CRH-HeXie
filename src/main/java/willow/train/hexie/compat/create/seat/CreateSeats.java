package willow.train.hexie.compat.create.seat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import willow.train.hexie.bridge.compat.CreatePlayerBridge.CreateSeatOperations;

public final class CreateSeats implements CreateSeatOperations {
    public static final CreateSeats INSTANCE = new CreateSeats();

    private CreateSeats() {}

    @Override public Entity createHexieSeatEntity(EntityType<?> type, Level level) {
        return new HexieSeatEntity(type, level);
    }
    @Override public boolean isHexieSeat(Entity entity) { return entity instanceof HexieSeatEntity; }

    @Override public Block createSeatYZ(BlockBehaviour.Properties properties) {
        return new SeatYZBlock(properties);
    }

    @Override public Block createRZSeat(BlockBehaviour.Properties properties) {
        return new RZSeatBlock(properties);
    }

    @Override public Block createCabSeat(BlockBehaviour.Properties properties) {
        return CRH2DriverSeatBlock.cabSeat(properties);
    }

    @Override public Block createDriverSeat(BlockBehaviour.Properties properties) {
        return new CRH2DriverSeatBlock(properties);
    }
}
