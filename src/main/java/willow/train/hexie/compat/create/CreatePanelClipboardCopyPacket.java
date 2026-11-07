package willow.train.hexie.compat.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;

/** Read-only moving-panel request. There deliberately is no moving paste packet. */
public record CreatePanelClipboardCopyPacket(int entityId, UUID entityUuid, InteractionHand hand,
        BlockHitResult hit) implements CustomPacketPayload {
    public static final Type<CreatePanelClipboardCopyPacket> TYPE = new Type<>(Main.asResource("copy_panel_attachment"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreatePanelClipboardCopyPacket> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.entityId);
                buf.writeUUID(value.entityUuid);
                buf.writeEnum(value.hand);
                buf.writeBlockHitResult(value.hit);
            }, buf -> new CreatePanelClipboardCopyPacket(buf.readVarInt(), buf.readUUID(),
                    buf.readEnum(InteractionHand.class), buf.readBlockHitResult()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var entity = player.level().getEntity(entityId);
            if (entity instanceof AbstractContraptionEntity carrier && carrier.getUUID().equals(entityUuid))
                copy(player, carrier, hand, hit);
        });
    }

    /** Validate against captured state; reconstructed entities are never attached to a real Level. */
    public static boolean copy(ServerPlayer player, AbstractContraptionEntity carrier,
            InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() || !AllBlocks.CLIPBOARD.isIn(player.getItemInHand(hand))) return false;
        var resolved = CreatePanelInteractionTarget.validate(player, carrier, hit);
        if (resolved == null) return false;
        var target = resolved.attachment();
        if (!CreatePanelClipboard.copy(resolved.view(), target, player.getItemInHand(hand), player.registryAccess(), hit.getDirection())) return false;
        CreatePanelClipboard.feedback(player, target, false);
        return true;
    }

    public static final class CapturedView extends CreatePanelInteractionTarget.CapturedView {
        public CapturedView(AbstractContraptionEntity carrier) { super(carrier); }
    }

    @EventBusSubscriber(modid = "hexie", bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE, CODEC, CreatePanelClipboardCopyPacket::handle);
        }
    }
}
