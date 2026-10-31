package willow.train.hexie.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.blocks.locos.DrivingConsoleContext;
import willow.train.hexie.blocks.locos.TestDrivingConsoleBlock;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Action only: the server derives the seat, console and owning train. */
public record TestCabActionPacket(int vehicleId, int action) implements HexiePacket {
    public TestCabActionPacket(FriendlyByteBuf buffer) { this(buffer.readVarInt(), buffer.readVarInt()); }
    @Override public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(vehicleId); buffer.writeVarInt(action); }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.isSpectator() || player.getVehicle() == null || player.getVehicle().getId() != vehicleId
                    || action < -1 || action > 6) return;
            var cab = DrivingConsoleContext.find(player);
            if (cab == null || !(cab.seatAccess().blockState(cab.console()).getBlock() instanceof TestDrivingConsoleBlock)) return;
            if (cab.seatAccess().vehicle() instanceof SnapshotCarriageEntity car && car.motion() != null)
                car.motion().testCabAction(car, cab, player, action);
            else if (!(cab.seatAccess().vehicle() instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity)
                    && player.level().getBlockEntity(cab.console()) instanceof willow.train.hexie.blockentity.DrivingConsoleEntity bench
                    && action >= 0) bench.benchAction(action);
        });
        return true;
    }
}
