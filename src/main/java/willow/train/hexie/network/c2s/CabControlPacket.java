package willow.train.hexie.network.c2s;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.api.cab.CabSurfaceHost;
import willow.train.hexie.api.cab.DrivingConsoleContext;

/** A requested detent or button press, resolved against the server's occupied console. */
public record CabControlPacket(int vehicleId, BlockPos console, String controlId, int value) implements HexiePacket {
    private static final int MAX_ID_LENGTH = 256;

    public CabControlPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBlockPos(), buffer.readUtf(MAX_ID_LENGTH), buffer.readVarInt());
    }

    @Override public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(vehicleId);
        buffer.writeBlockPos(console);
        buffer.writeUtf(controlId, MAX_ID_LENGTH);
        buffer.writeVarInt(value);
    }

    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var vehicle = player.getVehicle();
            if (vehicle == null || vehicle.getId() != vehicleId || player.isSpectator()) return;
            var cab = DrivingConsoleContext.find(player);
            if (cab == null || cab.seatAccess().vehicle() != vehicle || !cab.console().equals(console)) return;
            var state = cab.seatAccess().blockState(console);
            if (state == null || !(state.getBlock() instanceof CabSurfaceHost host)) return;
            var control = host.control(state, controlId);
            if (control != null && control.accepts(value))
                cab.seatAccess().operateControl(cab, player, control, value);
        });
        return true;
    }
}
