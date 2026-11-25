package willow.train.hexie.sync.c2s;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public interface HexiePacket {


    boolean handle(IPayloadContext var1);


    void encode(FriendlyByteBuf var1);
}
