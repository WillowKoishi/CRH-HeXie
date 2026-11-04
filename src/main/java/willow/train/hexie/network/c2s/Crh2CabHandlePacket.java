package willow.train.hexie.network.c2s;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.blockentity.DrivingConsoleEntity;
import willow.train.hexie.blocks.emu.DrivingConsoleContext;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.init.block.BlockInit;
import willow.train.hexie.vehicle.control.Crh2CabHandle;

/** An absolute handle position. The server resolves the console from the player's actual seat. */
public record Crh2CabHandlePacket(int vehicleId, Crh2CabHandle handle, int notch) implements HexiePacket {
    public Crh2CabHandlePacket {
        Objects.requireNonNull(handle).checkNotch(notch);
    }

    public Crh2CabHandlePacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readEnum(Crh2CabHandle.class), buffer.readVarInt());
    }

    @Override public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(vehicleId);
        buffer.writeEnum(handle);
        buffer.writeVarInt(notch);
    }

    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var vehicle = player.getVehicle();
            if (vehicle == null || vehicle.getId() != vehicleId || player.isSpectator()) return;
            var cab = DrivingConsoleContext.find(player);
            if (cab == null || cab.seatAccess().vehicle() != vehicle
                    || !cab.seatAccess().blockState(cab.console()).is(BlockInit.CRH2A_NONSTANDARD_DRIVING_CONSOLE.get())) return;

            if (vehicle instanceof SnapshotCarriageEntity car && car.motion() != null)
                car.motion().updateCabControls(car, cab, player, (state, speed) -> handle.apply(state, notch, speed));
            else if (!(vehicle instanceof AbstractContraptionEntity)
                    && player.level().getBlockEntity(cab.console()) instanceof DrivingConsoleEntity bench)
                bench.setCrh2Handle(handle, notch);
        });
        return true;
    }
}
