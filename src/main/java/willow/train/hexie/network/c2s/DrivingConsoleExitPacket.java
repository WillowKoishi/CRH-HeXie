package willow.train.hexie.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.blocks.locos.DrivingConsoleContext;

/** Dismount through the server's normal seat/contraption passenger lifecycle. */
public record DrivingConsoleExitPacket(int vehicleId) implements HexiePacket {
    public DrivingConsoleExitPacket(FriendlyByteBuf buffer) { this(buffer.readVarInt()); }
    @Override public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(vehicleId); }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.getVehicle() != null && player.getVehicle().getId() == vehicleId
                    && DrivingConsoleContext.find(player) != null) player.stopRiding();
        });
        return true;
    }
}
