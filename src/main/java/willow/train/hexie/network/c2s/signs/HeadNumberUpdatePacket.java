package willow.train.hexie.network.c2s.signs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.blockentity.CRH2HeadEntity;
import willow.train.hexie.menu.HeadNumberMenu;
import willow.train.hexie.network.c2s.HexiePacket;

public record HeadNumberUpdatePacket(BlockPos pos, String number) implements HexiePacket {
    public HeadNumberUpdatePacket(FriendlyByteBuf buf) { this(buf.readBlockPos(), buf.readUtf(CRH2HeadEntity.MAX_LENGTH * 2)); }
    @Override public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeUtf(number, CRH2HeadEntity.MAX_LENGTH * 2); }
    @Override public boolean handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof HeadNumberMenu menu)
                    || !menu.pos.equals(pos) || !menu.stillValid(player)) return;
            if (player.level().getBlockEntity(pos) instanceof CRH2HeadEntity head) head.updateNumber(number);
        });
        return true;
    }
}
