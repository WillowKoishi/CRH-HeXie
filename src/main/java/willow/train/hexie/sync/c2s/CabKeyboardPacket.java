package willow.train.hexie.sync.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.compat.CreatePanelBridge;
import willow.train.hexie.bridge.compat.SnapshotCarriageView;
import willow.train.hexie.bridge.world.BlockEntityViews.DrivingConsoleEntityView;
import willow.train.hexie.core.api.cab.DrivingConsoleContext;

/** Keyboard gestures only. The server derives the occupied cab and validates its declared bindings. */
public record CabKeyboardPacket(int vehicleId, boolean toggleSimple, int mask) implements HexiePacket {
    public CabKeyboardPacket(FriendlyByteBuf buffer) { this(buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt()); }
    @Override public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(vehicleId); buffer.writeBoolean(toggleSimple); buffer.writeVarInt(mask);
    }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.isSpectator() || player.getVehicle() == null || player.getVehicle().getId() != vehicleId
                    || !HexieBridges.cabControl().simpleCabControls().validMask(mask)) return;
            var cab = DrivingConsoleContext.find(player);
            if (cab == null) return;
            if (cab.seatAccess().vehicle() instanceof SnapshotCarriageView car && car.motion() != null)
                car.motion().updateCabKeyboard(player, car, cab.console(), toggleSimple, mask);
            else if (CreatePanelBridge.forCarrier(cab.seatAccess().vehicle()) == null
                    && player.level().getBlockEntity(cab.console()) instanceof DrivingConsoleEntityView bench)
                bench.benchKeyboard(player, toggleSimple, mask);
        });
        return true;
    }
}
