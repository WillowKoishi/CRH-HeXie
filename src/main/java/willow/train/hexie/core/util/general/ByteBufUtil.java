package willow.train.hexie.core.util.general;

import io.netty.buffer.ByteBufAllocator;
import net.minecraft.network.FriendlyByteBuf;

public class ByteBufUtil {

    public static FriendlyByteBuf defaultBuf() {
        return new FriendlyByteBuf(ByteBufAllocator.DEFAULT.buffer());
    }

}
