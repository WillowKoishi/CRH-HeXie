package willow.train.hexie.mixins.mixin;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.track.CurvedTrackSelectionPacket;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import willow.train.hexie.item.semaphore.ThroughSignalItem;

@Mixin(value = CurvedTrackSelectionPacket.class, remap = false)
public abstract class MixinCurvedTrackSelectionPacket {
    @ModifyVariable(
            method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/trains/track/TrackBlockEntity;)V",
            at = @At("STORE"), ordinal = 0, require = 1)
    private EdgePointType<?> hexie$useSignalType(EdgePointType<?> original, ServerPlayer player,
                                               TrackBlockEntity track) {
        return player.getMainHandItem().getItem() instanceof ThroughSignalItem ? EdgePointType.SIGNAL : original;
    }
}
