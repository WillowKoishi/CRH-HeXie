package willow.train.hexie.mixins.mixin.client;

import com.simibubi.create.content.trains.track.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.blocks.track.standard.TrackBallast;
import willow.train.hexie.init.track.TrackMaterialInit;
import willow.train.hexie.network.c2s.TrackBallastPacket;

@Mixin(value=CurvedTrackInteraction.class,remap=false)
public abstract class MixinCurvedBallastInteraction {
    @Inject(method="onClickInput",at=@At("HEAD"),cancellable=true,require=1)
    private static void hexie$paint(InputEvent.InteractionKeyMappingTriggered event,CallbackInfoReturnable<Boolean> cir) {
        var selection=TrackBlockOutline.result;var player=Minecraft.getInstance().player;
        if(!event.isUseItem()||selection==null||player==null||!TrackBallast.canPaint(player,player.getMainHandItem()))return;
        var curve=selection.blockEntity().getConnections().get(selection.loc().curveTarget());
        if(curve==null||curve.getMaterial()!=TrackMaterialInit.STANDARD)return;
        PacketDistributor.sendToServer(new TrackBallastPacket(selection.blockEntity().getBlockPos(),selection.loc().curveTarget(),selection.loc().segment()));
        player.swing(InteractionHand.MAIN_HAND);cir.setReturnValue(true);
    }
}
