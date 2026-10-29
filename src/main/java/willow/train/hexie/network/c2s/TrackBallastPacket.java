package willow.train.hexie.network.c2s;

import com.simibubi.create.content.trains.track.TrackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import willow.train.hexie.Main;
import willow.train.hexie.blocks.track.standard.TrackBallast;
import willow.train.hexie.init.track.TrackMaterialInit;

public record TrackBallastPacket(BlockPos origin,BlockPos target,int segment) implements CustomPacketPayload {
    public static final Type<TrackBallastPacket> TYPE=new Type<>(Main.asResource("track_ballast"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TrackBallastPacket> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeBlockPos(p.origin);buf.writeBlockPos(p.target);buf.writeVarInt(p.segment);},
            buf->new TrackBallastPacket(buf.readBlockPos(),buf.readBlockPos(),buf.readVarInt()));
    public void handle(IPayloadContext context) {
        context.enqueueWork(()->{
            if(!(context.player() instanceof ServerPlayer player))return;
            var level=player.serverLevel();var held=player.getMainHandItem();
            if(!TrackBallast.canPaint(player,held)||!level.hasChunkAt(origin)||!level.hasChunkAt(target)
                    ||!level.mayInteract(player,origin)||!level.mayInteract(player,target)
                    ||!(level.getBlockEntity(origin) instanceof TrackBlockEntity track))return;
            var curve=track.getConnections().get(target);
            if(curve==null||curve.getMaterial()!=TrackMaterialInit.STANDARD||segment<0||segment>curve.getSegmentCount())return;
            var hit=curve.getPosition(curve.getSegmentT(segment)).add(0,TrackMaterialInit.RAIL_SURFACE_HEIGHT,0);
            if(!player.canInteractWithBlock(BlockPos.containing(hit),1))return;
            var eye=player.getEyePosition();var delta=hit.subtract(eye);
            if(delta.lengthSqr()>1e-6&&player.getLookAngle().dot(delta.normalize())<.7)return;
            var obstruction=level.clip(new ClipContext(eye,hit,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(obstruction.getType()!=HitResult.Type.MISS&&obstruction.getLocation().distanceTo(eye)+1<delta.length())return;
            TrackBallast.paint(track,target,((BlockItem)held.getItem()).getBlock().defaultBlockState());
        });
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
