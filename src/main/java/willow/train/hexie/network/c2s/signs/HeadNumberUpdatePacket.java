package willow.train.hexie.network.c2s.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.menu.HeadNumberMenu;
import willow.train.hexie.network.c2s.HexiePacket;

public record HeadNumberUpdatePacket(BlockPos pos, String number) implements HexiePacket {
    public HeadNumberUpdatePacket(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readUtf(CRH2HeadEntity.MAX_LENGTH * 2)); }
    @Override public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeUtf(number, CRH2HeadEntity.MAX_LENGTH * 2); }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> SignEditSubmission.commit(context.player(), pos, CRH2HeadEntity.class,
                head -> head.updateNumber(number)));
        return true;
    }
}
