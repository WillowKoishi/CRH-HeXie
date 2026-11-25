package willow.train.hexie.render.door;

// split from willow/train/hexie/client/render/blockentity/CustomRenderedDoorRenderer.java

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import willow.train.hexie.bridge.world.BlockEntityViews.CustomRenderedDoorEntityView;
import willow.train.hexie.bridge.world.PartBlockBridge.ModelRenderBoundsView;
import willow.train.hexie.render.carriage.CarriageBlockVisual;
import willow.train.hexie.render.carriage.CarriageTransparencyCompat;

import static willow.train.hexie.render.door.CarriageDoorParts.drawParts;
import static willow.train.hexie.render.door.CarriageDoorParts.isShellDoor;
import static willow.train.hexie.render.door.DoorShellLighting.shellBuffers;

public class CustomRenderedDoorRenderer implements BlockEntityRenderer<BlockEntity> {

    public CustomRenderedDoorRenderer(BlockEntityRendererProvider.Context pContext) {}

    @Override
    public void render(
            BlockEntity blockEntity,
            float partial,
            PoseStack pose,
            MultiBufferSource buffer,
            int light,
            int overlay) {
        if (!(blockEntity instanceof CustomRenderedDoorEntityView entity)) return;
        BlockState state = entity.asBlockEntity().getBlockState();
        if (!entity.isLowerSide(state)) return;
        boolean shell = isShellDoor(state);
        boolean glassOnly = CarriageBlockVisual.ownsParts(entity.asBlockEntity());
        MultiBufferSource materials = shell ? shellBuffers(entity, pose, buffer) : buffer;
        pose.pushPose();
        drawParts(entity, entity.crh2Progress(partial), pose, (model, layer, transform) -> {
            if (glassOnly && layer != RenderType.translucent()) return;
            SuperByteBuffer part = CachedBuffers.partial(model, state);
            if (shell) part.disableDiffuse();
            RenderType output = layer == RenderType.translucent() && CarriageTransparencyCompat.required()
                    ? CarriageTransparencyCompat.glassLayer() : layer;
            part.light(light).overlay(overlay).renderInto(transform, materials.getBuffer(output));
        });
        pose.popPose();
    }

    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(BlockEntity entity) {
        if (entity.getBlockState().getBlock() instanceof ModelRenderBoundsView model)
            return model.modelRenderBounds(entity.getBlockState()).move(entity.getBlockPos());
        return new net.minecraft.world.phys.AABB(entity.getBlockPos()).inflate(1.5);
    }

}
