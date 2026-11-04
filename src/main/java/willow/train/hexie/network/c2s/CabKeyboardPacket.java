package willow.train.hexie.network.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.api.cab.CabControlHost;
import willow.train.hexie.blocks.locos.DrivingConsoleContext;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;
import willow.train.hexie.vehicle.control.SimpleCabControls;

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
                    || !SimpleCabControls.validMask(mask)) return;
            var cab = DrivingConsoleContext.find(player);
            if (cab == null || !(cab.seatAccess().blockState(cab.console()).getBlock() instanceof CabControlHost host)) return;
            if (cab.seatAccess().vehicle() instanceof SnapshotCarriageEntity car && car.motion() != null)
                car.motion().updateCabKeyboard(player, car, cab.console(), toggleSimple, mask);
            else if (!(cab.seatAccess().vehicle() instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity)
                    && player.level().getBlockEntity(cab.console()) instanceof willow.train.hexie.blockentity.DrivingConsoleEntity bench) {
                var actions = host.keyboardActions(bench.getBlockState(), bench.controls().simple());
                if (toggleSimple && !actions.contains(CabControlHost.KeyboardAction.TOGGLE_SIMPLE)) return;
                int permitted = actions.stream().mapToInt(CabControlHost.KeyboardAction::inputMask).reduce(0, (a, b) -> a | b);
                if ((mask & ~permitted) == 0) bench.benchKeyboard(toggleSimple, mask,
                        actions.contains(CabControlHost.KeyboardAction.APPROACH));
            }
        });
        return true;
    }
}
