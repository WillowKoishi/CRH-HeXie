package willow.train.hexie.sync.c2s.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH2HeadEntityView;
import willow.train.hexie.bridge.world.WorldBlockBridge.CRH2HeadEntityOperations;
import willow.train.hexie.sync.c2s.HexiePacket;

public record HeadNumberUpdatePacket(BlockPos pos, String number, int menuId) implements HexiePacket {
    public HeadNumberUpdatePacket(BlockPos pos, String number) { this(pos, number, -1); }
    public HeadNumberUpdatePacket withMenuId(int id) { return new HeadNumberUpdatePacket(pos, number, id); }
    public HeadNumberUpdatePacket(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readUtf(CRH2HeadEntityOperations.MAX_LENGTH * 2), buf.isReadable()?buf.readVarInt():-1); }
    @Override public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeUtf(number, CRH2HeadEntityOperations.MAX_LENGTH * 2); buf.writeVarInt(menuId); }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> SignEditSubmission.commit(context.player(), pos, menuId, CRH2HeadEntityView.class,
                head -> head.updateNumber(number)));
        return true;
    }
}
