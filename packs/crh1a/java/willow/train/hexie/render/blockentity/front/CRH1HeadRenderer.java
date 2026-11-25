package willow.train.hexie.render.blockentity.front;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.bridge.HexieBridges;
import willow.train.hexie.bridge.world.BlockEntityViews.CRH1HeadEntityView;
import willow.train.hexie.render.coupler.CRH1HeadLighting;
import willow.train.hexie.render.coupler.SnapshotCouplerRenderer;

public final class CRH1HeadRenderer implements BlockEntityRenderer<BlockEntity> {
    public CRH1HeadRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(BlockEntity blockEntity, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (!(blockEntity instanceof CRH1HeadEntityView head)) return;
        if (!head.isCarriageView())
            SnapshotCouplerRenderer.renderWorldAttachedParts(head.asBlockEntity().getBlockState(), head.asBlockEntity(), partialTick, pose,
                    CRH1HeadLighting.worldBuffers(head, pose, buffers), light, overlay);
    }
    @Override public AABB getRenderBoundingBox(BlockEntity head) {
        return HexieBridges.carriageBlock().crh1HeadGeometry().bounds(head.getBlockState().getValue(HexieBridges.carriageBlock().crh1HeadBlock().facing())).move(head.getBlockPos());
    }
    @Override public boolean shouldRenderOffScreen(BlockEntity head) { return true; }
}
