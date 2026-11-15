package willow.train.hexie.client.render.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import willow.train.hexie.blockentity.CRH1HeadEntity;
import willow.train.hexie.blocks.emu.crh1a.CRH1HeadBlock;
import willow.train.hexie.blocks.emu.crh1a.CRH1HeadGeometry;
import willow.train.hexie.client.render.SnapshotCouplerRenderer;
import willow.train.hexie.client.render.CRH1HeadLighting;

public final class CRH1HeadRenderer implements BlockEntityRenderer<CRH1HeadEntity> {
    public CRH1HeadRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(CRH1HeadEntity head, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (!head.isCarriageView())
            SnapshotCouplerRenderer.renderWorldAttachedParts(head.getBlockState(), head, partialTick, pose,
                    CRH1HeadLighting.worldBuffers(head, pose, buffers), light, overlay);
    }
    @Override public AABB getRenderBoundingBox(CRH1HeadEntity head) {
        return CRH1HeadGeometry.bounds(head.getBlockState().getValue(CRH1HeadBlock.FACING)).move(head.getBlockPos());
    }
    @Override public boolean shouldRenderOffScreen(CRH1HeadEntity head) { return true; }
}
