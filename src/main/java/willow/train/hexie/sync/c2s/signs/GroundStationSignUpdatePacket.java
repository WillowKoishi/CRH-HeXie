package willow.train.hexie.sync.c2s.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.bridge.world.BlockEntityViews.GroundStationSignEntityView;
import willow.train.hexie.sync.c2s.HexiePacket;

public class GroundStationSignUpdatePacket implements HexiePacket {
    private final BlockPos pos;
    private final String[] fields;
    public GroundStationSignUpdatePacket(BlockPos pos, String[] fields) { this.pos = pos; this.fields = fields.clone(); }
    public GroundStationSignUpdatePacket(FriendlyByteBuf buf) {
        pos = buf.readBlockPos(); fields = new String[4];
        for (int i = 0; i < 4; i++) fields[i] = buf.readUtf(128);
    }
    @Override public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); for (String field : fields) buf.writeUtf(field, 128); }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> SignEditSubmission.commit(context.player(), pos, GroundStationSignEntityView.class,
                sign -> sign.update(fields)));
        return true;
    }
}
