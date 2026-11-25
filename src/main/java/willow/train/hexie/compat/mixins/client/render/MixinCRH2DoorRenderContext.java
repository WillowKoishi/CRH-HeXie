package willow.train.hexie.compat.mixins.client.render;

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
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH1DoorBlockView;
import willow.train.hexie.bridge.world.CarriageBlockBridge.CRH2DoorBlockView;

/** The carriage visual owns its instanced parts; immediate doors use Create's exact world-light transform. */
@Mixin(BlockEntityRenderHelper.class)
public abstract class MixinCRH2DoorRenderContext {
    @WrapOperation(method = "renderBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"), remap = false)
    private static void hexie$renderCarriagePart(BlockEntityRenderer<BlockEntity> renderer, BlockEntity entity,
            float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Operation<Void> original,
            List<BlockEntity> blockEntities, BitSet shouldRender, BitSet errors, VirtualRenderWorld renderLevel,
            Level realLevel, PoseStack outerPose, Matrix4f lightTransform, MultiBufferSource outerBuffers, float outerPartial) {
        if (HexieBridges.Client.carriageRender().carriageBlockVisual().replacesRenderer(entity)) return;
        var block = entity.getBlockState().getBlock();
        if (realLevel instanceof ClientLevel client) {
            if (block instanceof CRH1DoorBlockView) {
                var context = HexieBridges.Client.doorRender().crh1DoorRenderer();
                var previous = context.installLighting(client, lightTransform, entity.getBlockPos());
                try {
                    original.call(renderer, entity, partial, pose, buffers, light, overlay);
                } finally {
                    context.restoreLighting(previous);
                }
                return;
            }
            if (block instanceof CRH2DoorBlockView) {
                var context = HexieBridges.Client.doorRender().crh2DoorRenderContext();
                var previous = context.install(client, lightTransform, entity.getBlockPos());
                try {
                    original.call(renderer, entity, partial, pose, buffers, light, overlay);
                } finally {
                    context.restore(previous);
                }
                return;
            }
        }
        original.call(renderer, entity, partial, pose, buffers, light, overlay);
    }
}
