package willow.train.hexie.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;

/** Contains only the clicked target; edits are accepted through the server's active menu session. */
public record CreatePanelEditorOpenPacket(int entityId, UUID entityUuid, InteractionHand hand,
        BlockHitResult hit) implements CustomPacketPayload {
    public static final Type<CreatePanelEditorOpenPacket> TYPE = new Type<>(Main.asResource("open_panel_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreatePanelEditorOpenPacket> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.entityId); buf.writeUUID(value.entityUuid);
                buf.writeEnum(value.hand); buf.writeBlockHitResult(value.hit);
            }, buf -> new CreatePanelEditorOpenPacket(buf.readVarInt(), buf.readUUID(),
                    buf.readEnum(InteractionHand.class), buf.readBlockHitResult()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.level().getEntity(entityId) instanceof AbstractContraptionEntity carrier
                    && carrier.getUUID().equals(entityUuid)) CreatePanelEditor.open(player, carrier, hand, hit);
        });
    }
}
