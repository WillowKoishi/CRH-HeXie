package willow.train.hexie.mixins.mixin.cee;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import willow.train.hexie.compat.cee.PantographBaseModels;
import willow.train.hexie.compat.cee.PantographMounting;

@Pseudo
@Mixin(targets = "com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographMovementBehaviour", remap = false)
public abstract class PantographMovementRendererMixin {
    @WrapMethod(method = "renderInContraption")
    private void hexie$renderMounted(MovementContext context, VirtualRenderWorld world, ContraptionMatrices matrices,
            MultiBufferSource buffer, Operation<Void> original) {
        var model = matrices.getModel();
        model.pushPose();
        try {
            model.translate(0, PantographMounting.movingOffset(context), 0);
            // Baked quads need the complete transform. CEE applies the model transform to its arms separately.
            var pose = matrices.getViewProjection();
            pose.pushPose();
            try {
                ContraptionMatrices.transform(pose, model);
                PantographBaseModels.render(context.state, pose, buffer,
                        LevelRenderer.getLightColor(world, context.localPos), OverlayTexture.NO_OVERLAY);
            } finally {
                pose.popPose();
            }
            original.call(context, world, matrices, buffer);
        } finally {
            model.popPose();
        }
    }
}
