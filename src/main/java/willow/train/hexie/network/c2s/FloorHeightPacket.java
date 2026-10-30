package willow.train.hexie.network.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.bogeys.BogeyFloorData;
import willow.train.hexie.item.FloorSwitcherItem;
import willow.train.hexie.network.ServerHeldItemSync;

/** The slider retains its server-selected target until explicit confirmation or cancellation. */
public record FloorHeightPacket(InteractionHand hand, int hotbarSlot, int height, Action action) implements CustomPacketPayload {
    public enum Action { OPEN, CONFIRM, CANCEL }
    public FloorHeightPacket(InteractionHand hand, int hotbarSlot, int height) {
        this(hand, hotbarSlot, height, Action.CONFIRM);
    }
    public static final Type<FloorHeightPacket> TYPE = new Type<>(Main.asResource("floor_height"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FloorHeightPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.hand() == InteractionHand.MAIN_HAND);
                buffer.writeVarInt(packet.hotbarSlot());
                buffer.writeVarInt(packet.height());
                buffer.writeEnum(packet.action());
            }, buffer -> new FloorHeightPacket(buffer.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readEnum(Action.class)));

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) applyTo(player); });
    }

    public boolean applyTo(ServerPlayer player) {
        if (player.isSpectator() || !player.mayBuild() || !BogeyFloorData.valid(height)) return false;
        int selected = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
        if (hotbarSlot != selected || hand == InteractionHand.MAIN_HAND && (hotbarSlot < 0 || hotbarSlot > 8)
                || !(player.getItemInHand(hand).getItem() instanceof FloorSwitcherItem)) return false;
        if (action == Action.OPEN) {
            // Retain the target, but remove it from the short-release path even under low server TPS.
            FloorSwitcherItem.beginConfiguration(player);
            return true;
        }
        if (action == Action.CANCEL) {
            FloorSwitcherItem.cancelPending(player);
            return true;
        }
        FloorSwitcherItem.setHeight(player.getItemInHand(hand), height);
        FloorSwitcherItem.confirmConfiguration(player, hand, height);
        ServerHeldItemSync.afterInteraction(player, hand);
        return true;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
