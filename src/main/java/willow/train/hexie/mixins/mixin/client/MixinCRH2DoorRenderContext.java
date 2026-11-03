package willow.train.hexie.mixins.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.render.BlockEntityRenderHelper;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import java.util.BitSet;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import willow.train.hexie.blocks.locos.crh2a.CRH2DoorBlock;
import willow.train.hexie.client.render.CRH2DoorRenderContext;

/** Keep Create's renderer dispatch, exposing its authoritative light transform only while drawing a CRH2 door. */
@Mixin(BlockEntityRenderHelper.class)
public abstract class MixinCRH2DoorRenderContext {
    @WrapOperation(method = "renderBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"), remap = false)
    private static void hexie$doorWorldLight(BlockEntityRenderer<BlockEntity> renderer, BlockEntity entity,
            float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Operation<Void> original,
            List<BlockEntity> blockEntities, BitSet shouldRender, BitSet errors, VirtualRenderWorld renderLevel,
            Level realLevel, PoseStack outerPose, Matrix4f lightTransform, MultiBufferSource outerBuffers, float outerPartial) {
        if (!(entity.getBlockState().getBlock() instanceof CRH2DoorBlock) || !(realLevel instanceof ClientLevel client)) {
            original.call(renderer, entity, partial, pose, buffers, light, overlay);
            return;
        }
        var previous = CRH2DoorRenderContext.install(client, lightTransform, entity.getBlockPos());
        try {
            original.call(renderer, entity, partial, pose, buffers, light, overlay);
        } finally {
            CRH2DoorRenderContext.restore(previous);
        }
    }
}
