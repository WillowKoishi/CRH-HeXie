package willow.train.hexie.mixins.mixin.client;

import com.crystaelix.simurail.content.bogey.PhysicsBogeyBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.bogey.BogeyVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import willow.train.hexie.compat.simurail.PhysicalHeadBogeyBlockEntity;

/** Only the dynamic wheel/connector origins move; ShaftVisual's control-block transform is unchanged. */
@Pseudo
@Mixin(targets = "com.crystaelix.simurail.content.bogey.PhysicsBogeyVisual", remap = false)
public abstract class MixinPhysicalHeadBogeyVisual extends AbstractBlockEntityVisual<PhysicsBogeyBlockEntity> {
    @Shadow private BogeyVisual pivot;
    @Shadow private TransformedInstance frontHead;
    @Shadow private TransformedInstance backHead;
    @Shadow private TransformedInstance[] frontCable;
    @Shadow private TransformedInstance[] backCable;

    protected MixinPhysicalHeadBogeyVisual(VisualizationContext context, PhysicsBogeyBlockEntity entity, float partialTick) {
        super(context, entity, partialTick);
    }

    @Inject(method = "beginFrame", at = @At("HEAD"), cancellable = true)
    private void hexie$hideSnapshotProxy(DynamicVisual.Context context, CallbackInfo ci) {
        if (!(blockEntity instanceof PhysicalHeadBogeyBlockEntity head)
                || !head.isCarriageView() && head.headBogeyEnabled()) return;
        if (pivot != null) pivot.hide();
        frontHead.setZeroTransform().setChanged();
        backHead.setZeroTransform().setChanged();
        for (var instance : frontCable) instance.setZeroTransform().setChanged();
        for (var instance : backCable) instance.setZeroTransform().setChanged();
        ci.cancel();
    }

    @WrapOperation(method = "beginFrame", at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(Lnet/minecraft/core/Vec3i;)Ldev/engine_room/flywheel/lib/transform/Translate;"), require = 1)
    private Translate<?> hexie$wheelOrigin(PoseTransformStack pose, Vec3i position, Operation<Translate<?>> original) {
        var result = original.call(pose, position);
        if (blockEntity instanceof PhysicalHeadBogeyBlockEntity head) {
            var offset = head.attachmentOffset();
            pose.translate((float) offset.x, (float) offset.y, (float) offset.z);
        }
        return result;
    }

    @WrapOperation(method = "beginFrame", at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/lib/instance/TransformedInstance;translate(Lnet/minecraft/core/Vec3i;)Ldev/engine_room/flywheel/lib/transform/Translate;"), require = 4)
    private Translate<?> hexie$connectorOrigin(TransformedInstance instance, Vec3i position, Operation<Translate<?>> original) {
        var result = original.call(instance, position);
        if (blockEntity instanceof PhysicalHeadBogeyBlockEntity head) {
            var offset = head.attachmentOffset();
            instance.translate((float) offset.x, (float) offset.y, (float) offset.z);
        }
        return result;
    }
}
