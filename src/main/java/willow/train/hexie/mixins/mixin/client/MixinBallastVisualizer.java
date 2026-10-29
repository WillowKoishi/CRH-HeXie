package willow.train.hexie.mixins.mixin.client;

import com.simibubi.create.content.trains.track.TrackBlockEntity;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import willow.train.hexie.client.render.track.TrackBallastRenderer;

@Mixin(value = SimpleBlockEntityVisualizer.class, remap = false)
public abstract class MixinBallastVisualizer {
    @Inject(method = "skipVanillaRender", at = @At("HEAD"), cancellable = true)
    private void hexie$keepBallastRenderer(BlockEntity be, CallbackInfoReturnable<Boolean> cir) {
        // Flywheel continues to draw rails; the normal renderer adds only the ballast.
        if (be instanceof TrackBlockEntity track && TrackBallastRenderer.hasBallast(track)) cir.setReturnValue(false);
    }
}
