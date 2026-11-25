package willow.train.hexie.sync.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemMode;
import willow.train.hexie.bridge.interaction.ToolOpsBridge.FloorSwitcherItemView;
import willow.train.hexie.bridge.sync.SyncBridge.FloorHeightPacketAction;
import willow.train.hexie.core.HexieConstants;
import willow.train.hexie.sync.channel.ServerHeldItemSync;

/** The slider retains its server-selected target until explicit confirmation or cancellation. */
public record FloorHeightPacket(InteractionHand hand, int hotbarSlot, int height, FloorHeightPacketAction action) implements CustomPacketPayload {
    public FloorHeightPacket(InteractionHand hand, int hotbarSlot, int height) {
        this(hand, hotbarSlot, height, FloorHeightPacketAction.CONFIRM);
    }
    public static final Type<FloorHeightPacket> TYPE = new Type<>(HexieConstants.asResource("floor_height"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FloorHeightPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.hand() == InteractionHand.MAIN_HAND);
                buffer.writeVarInt(packet.hotbarSlot());
                buffer.writeVarInt(packet.height());
                buffer.writeEnum(packet.action());
            }, buffer -> new FloorHeightPacket(buffer.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readEnum(FloorHeightPacketAction.class)));

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) applyTo(player); });
    }

    public boolean applyTo(ServerPlayer player) {
        if (player.isSpectator() || !player.mayBuild()) return false;
        int selected = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        if (hotbarSlot != selected || hand == InteractionHand.MAIN_HAND && (hotbarSlot < 0 || hotbarSlot > 8)
                || !(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItemView)) return false;
        var wrench = HexieBridges.toolOps().floorSwitcherItem();
        if (action == FloorHeightPacketAction.CONFIRM_DOOR_DIRECTION)
            return wrench.confirmDoorDirection(player, hand, height);
        if (wrench.configuration(player).mode() == FloorSwitcherItemMode.DOOR_DIRECTION) {
            if (action == FloorHeightPacketAction.CONFIRM || (height != 0 && height != 1)) return false;
        } else if (!wrench.valid(player.getItemInHand(hand), height)) return false;
        if (action == FloorHeightPacketAction.OPEN) {
            // Retain the target, but remove it from the short-release path even under low server TPS.
            wrench.beginConfiguration(player);
            return true;
        }
        if (action == FloorHeightPacketAction.CANCEL) {
            wrench.cancelPending(player);
            return true;
        }
        wrench.setHeight(player.getItemInHand(hand), height);
        wrench.confirmConfiguration(player, hand, height);
        ServerHeldItemSync.afterInteraction(player, hand);
        return true;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
