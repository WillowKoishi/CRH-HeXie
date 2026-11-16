package willow.train.hexie.network.s2c;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.emu.crh1a.CRH1HeadAnimation;
import willow.train.hexie.compat.create.SnapshotCarriageEntity;

/** Only the authored cover/extension timeline crosses the network, never arbitrary block data. */
public record CRH1HeadAnimationPacket(int entityId, UUID entityUuid, BlockPos position,
                                      float from, long start, boolean open) implements CustomPacketPayload {
    public static final Type<CRH1HeadAnimationPacket> TYPE = new Type<>(Main.asResource("crh1_head_animation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CRH1HeadAnimationPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.entityId());
                buffer.writeUUID(packet.entityUuid());
                buffer.writeBlockPos(packet.position());
                buffer.writeFloat(packet.from());
                buffer.writeLong(packet.start());
                buffer.writeBoolean(packet.open());
            }, buffer -> new CRH1HeadAnimationPacket(buffer.readVarInt(), buffer.readUUID(), buffer.readBlockPos(),
                    buffer.readFloat(), buffer.readLong(), buffer.readBoolean()));

    public static CRH1HeadAnimationPacket create(SnapshotCarriageEntity carriage, StructureBlockInfo info) {
        var data = info.nbt();
        return new CRH1HeadAnimationPacket(carriage.getId(), carriage.getUUID(), info.pos(),
                data.getFloat(CRH1HeadAnimation.FROM), data.getLong(CRH1HeadAnimation.START),
                info.state().getValue(BlockStateProperties.OPEN));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> willow.train.hexie.client.render.CRH1HeadAnimationUpdates.apply(context.player().level(), this));
    }
}
