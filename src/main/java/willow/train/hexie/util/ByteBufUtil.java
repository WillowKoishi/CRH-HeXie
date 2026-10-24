package willow.train.hexie.util;

import io.netty.buffer.ByteBufAllocator;
import net.minecraft.network.FriendlyByteBuf;

public class ByteBufUtil {

    public static FriendlyByteBuf defaultBuf() {
        return new FriendlyByteBuf(ByteBufAllocator.DEFAULT.buffer());
    }

    public static FriendlyByteBuf defaultBuf(int initCapacity) {
        return new FriendlyByteBuf(ByteBufAllocator.DEFAULT.buffer(initCapacity));
    }
}
