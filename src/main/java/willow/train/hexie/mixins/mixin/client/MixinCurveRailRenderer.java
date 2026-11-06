package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.compat.create.client.CurveRailModel;

@Mixin(value = TrackRenderer.class, remap = false)
abstract class MixinCurveRailRenderer {
    @WrapOperation(method = "renderBezierTurn", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/createmod/catnip/render/CachedBuffers;partial(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/createmod/catnip/render/SuperByteBuffer;"))
    private static SuperByteBuffer hexie$rail(PartialModel model, BlockState state, Operation<SuperByteBuffer> original,
            @Local(argsOnly = true) BezierConnection curve, @Local(ordinal = 0) int index) {
        return CurveRailModel.applies(curve) ? CurveRailModel.get(curve, index,
                model == curve.getMaterial().getModelHolder().leftSegment()).buffer() : original.call(model, state);
    }
}
